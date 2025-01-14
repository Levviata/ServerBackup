package de.sebli.serverbackup.utils;

import com.dropbox.core.*;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.*;
import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.utils.enums.TaskPurpose;
import de.sebli.serverbackup.utils.enums.TaskType;
import de.sebli.serverbackup.utils.records.Task;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static de.sebli.serverbackup.ServerBackupPlugin.sendMessageWithLogs;
import static de.sebli.serverbackup.utils.FileUtil.tryDeleteFile;
import static de.sebli.serverbackup.utils.TaskUtils.*;

public class DropboxManager {

    private final CommandSender sender;
    public DropboxManager(CommandSender sender) {
        this.sender = sender;
    }

    private static final String ACCESS_TOKEN = Configuration.cloudInfo.getString("Cloud.Dropbox.AccessToken");
    private static final String CLOUD_RT = "Cloud.Dropbox.RT";
    private static final String MESSAGE_SUCCESSFUL_UPLOAD = "Dropbox: Upload successfully. Backup stored on your dropbox account.";

    private static final long CHUNKED_UPLOAD_CHUNK_SIZE = 16L << 20;
    private static final int CHUNKED_UPLOAD_MAX_ATTEMPTS = 5;

    private static final long BASE_RETRY_DELAY_TICKS = 20L * 3;

    private static Task currentTask;

    public void uploadToDropbox(String filePath) {
        File file = new File(filePath);

        if (!file.getPath().contains(Configuration.backupDestination.replace("/", ""))) {
            file = Paths.get(Configuration.backupDestination, filePath).toFile();
        }

        if (!file.exists()) {
            String backupNotFound = "Dropbox: Backup '" + file.getName() + "' not found.";

            sendMessageWithLogs(backupNotFound, sender);
            return;
        }

        DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/ServerBackupG").build();

        String appKey = Configuration.cloudInfo.getString("Cloud.Dropbox.AppKey");
        String secretKey = Configuration.cloudInfo.getString("Cloud.Dropbox.AppSecret");

        DbxClientV2 client;
        DbxCredential credential;

        if (!Configuration.cloudInfo.contains(CLOUD_RT)) {
            DbxAppInfo appInfo = new DbxAppInfo(appKey, secretKey);
            DbxWebAuth webAuth = new DbxWebAuth(config, appInfo);
            DbxAuthFinish authFinish = null;
            try {
                authFinish = webAuth.finishFromCode(ACCESS_TOKEN);
            } catch (DbxException e) {
                ServerBackupPlugin.getPluginInstance().getLogger().warning(MessageFormat.format("Error during Dropbox authentication process: {0}", e.getMessage()));
            }
            credential = new DbxCredential(Objects.requireNonNull(authFinish).getAccessToken(), 60L, authFinish.getRefreshToken(), appKey, secretKey);

            Configuration.cloudInfo.set(CLOUD_RT, authFinish.getRefreshToken());
            Configuration.saveCloud();
        } else {
            credential = new DbxCredential("", 0L, Configuration.cloudInfo.getString(CLOUD_RT), appKey, secretKey);
        }

        client = new DbxClientV2(config, credential);

        sender.sendMessage("Dropbox: Uploading backup [" + file.getName() + "] ...");

        String des = ServerBackupPlugin.getPluginInstance().getConfig().getString("CloudBackup.Options.Destination").replace("/", "");

        try (InputStream in = new FileInputStream(filePath)) {
            // Step 1: Upload session start
            UploadSessionStartUploader uploadSessionStart = client.files().uploadSessionStart();
            UploadSessionStartResult uploadSessionStartResult = uploadSessionStart.uploadAndFinish(in);
            String sessionId = uploadSessionStartResult.getSessionId();

            // Step 2: Upload large file in chunks
            long fileSize = file.length();
            byte[] buffer = new byte[4 * 1000 * 1000]; // 4 MB chunk size
            long uploaded = 0;

            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buffer, 0, bytesRead);
                UploadSessionCursor cursor = new UploadSessionCursor(sessionId, uploaded);

                // Append data to the session
                client.files().uploadSessionAppendV2(cursor).uploadAndFinish(byteArrayInputStream, bytesRead);
                uploaded += bytesRead;
            }

            // Step 3: Upload session finish
            UploadSessionCursor cursor = new UploadSessionCursor(sessionId, fileSize);
            CommitInfo commitInfo = CommitInfo.newBuilder("/" + (des.isEmpty() ? "" : des + "/") + file.getName())
                    .withClientModified(new Date())
                    .withMode(WriteMode.ADD)
                    .build();
            FileMetadata metadata = client.files().uploadSessionFinish(cursor, commitInfo).finish();
        } catch (UploadErrorException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (DbxException e) {
            throw new RuntimeException(e);
        } finally {
            sendMessageWithLogs(MESSAGE_SUCCESSFUL_UPLOAD, sender);

            if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("CloudBackup.Options.DeleteLocalBackup")) {
                tryDeleteFile(file);
                ServerBackupPlugin.getPluginInstance().getLogger().info("File [" + file.getPath() + "] deleted.");
            }
        }
    }

    private void setProgress(File file, long uploaded, long size, boolean finished) {
        List<Task> tasksToRemove = getTasks().stream()
                .filter(task -> task.type() == TaskType.DROPBOX && task.purpose() == TaskPurpose.PROGRESS)
                .toList(); // Collect matching tasks into a separate list

        for (Task task : tasksToRemove) {
            removeTask(task); // remove old progress
        }

        if (finished) {
            removeTask(currentTask);
        } else {
            setCurrentTask(addTask(TaskType.DROPBOX, TaskPurpose.PROGRESS, file.getName() + ", Progress: " + Math.round((uploaded / (double) size) * 100) + "%"));
        }
    }

    private void scheduleRetry(CommandSender sender, DbxClientV2 client, File file, String dbxPath) {
        new BukkitRunnable() {
            @Override
            public void run() {

            }
        }.runTaskLater(ServerBackupPlugin.getPluginInstance(), BASE_RETRY_DELAY_TICKS);
    }

    private static void setCurrentTask(Task taskIn) {
        currentTask = taskIn;
    }
}
