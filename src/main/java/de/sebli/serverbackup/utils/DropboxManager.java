package de.sebli.serverbackup.utils;

import com.dropbox.core.*;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.util.IOUtil;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.*;
import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.utils.enums.TaskPurpose;
import de.sebli.serverbackup.utils.enums.TaskType;
import de.sebli.serverbackup.utils.records.AppendResult;
import de.sebli.serverbackup.utils.records.StartResult;
import de.sebli.serverbackup.utils.records.Task;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

        String des = Objects.requireNonNull(
                ServerBackupPlugin.getPluginInstance().getConfig().getString("CloudBackup.Options.Destination")).replace("/", "");
        des = "/" + (des.isEmpty() ? "" : des + "/");

        if (file.length() > (100 * 1000 * 1000)) {
            chunkedUploadFile(sender, client, file, des + file.getName());
        } else {
            uploadFile(sender, client, file, des + file.getName());
        }
    }

    private void chunkedUploadFile(CommandSender sender, DbxClientV2 dbxClient, File file, String dbxPath) {
        long size = file.length();

        long uploaded = 0L;

        IOUtil.ProgressListener progressListener = new IOUtil.ProgressListener() {
            long uploadedBytes = 0;

            @Override
            public void onProgress(long l) {
                setProgress(file, l + uploadedBytes, size, false);
                if (l == CHUNKED_UPLOAD_CHUNK_SIZE) uploadedBytes += CHUNKED_UPLOAD_CHUNK_SIZE;
            }
        };

        String sessionId;
        for (int attemptNumber = 0; attemptNumber < CHUNKED_UPLOAD_MAX_ATTEMPTS; ++attemptNumber) {

            long retryDelayTicks = BASE_RETRY_DELAY_TICKS * (1L << attemptNumber); // Exponential backoff: baseDelay * 2^attempt

            double retryDelaySeconds = retryDelayTicks / 20.0; // Convert to seconds

            try (InputStream streamIn = new FileInputStream(file)) {
                long bytesSkipped = streamIn.skip(uploaded);

                if (bytesSkipped < uploaded) { // bytes skipped are amount to less than the uploaded bytes
                    String formattedMessage = MessageFormat.format(
                            "Dropbox: Only skipped {0} bytes out of a total of {1}! Retrying in {2} seconds...",
                            bytesSkipped,
                            uploaded,
                            retryDelaySeconds
                    );

                    ServerBackupPlugin.getPluginInstance().getLogger().warning(formattedMessage);
                    scheduleRetry(sender, dbxClient, file, dbxPath);
                    return; // Stop current call
                }

                // Start
                Optional<StartResult> resultStart = startChunkedUpload(dbxClient, file, size, uploaded, streamIn);

                if (resultStart.isPresent()) {
                    sessionId = resultStart.get().sessionId();
                    uploaded = resultStart.get().uploaded();
                } else {
                    String formattedMessage = MessageFormat.format(
                            "Dropbox: Couldn''t start chunked uploading to Dropbox because the result is missing! Retrying in {0} seconds...",
                            retryDelaySeconds
                    );

                    ServerBackupPlugin.getPluginInstance().getLogger().severe(formattedMessage);
                    scheduleRetry(sender, dbxClient, file, dbxPath);
                    return; // Stop current call
                }

                UploadSessionCursor cursor = new UploadSessionCursor(Objects.requireNonNull(sessionId), uploaded);

                boolean appendFailed = false;

                // Append
                while ((size - uploaded) > CHUNKED_UPLOAD_CHUNK_SIZE) {
                    Optional<AppendResult> resultAppend = appendChunkedUpload(dbxClient, file, sessionId, uploaded, size, streamIn, cursor);

                    if (resultAppend.isPresent()) {
                        uploaded = resultAppend.get().uploaded();
                        cursor = resultAppend.get().cursor();
                    } else {
                        String formattedMessage = MessageFormat.format(
                                "Dropbox: Couldn''t start chunked uploading to Dropbox because the result is missing! Retrying in {0} seconds...",
                                retryDelaySeconds);

                                ServerBackupPlugin.getPluginInstance().getLogger().severe(formattedMessage);
                        appendFailed = true;
                        break; // Exit the while loop and stop the method call below
                    }
                }

                if (appendFailed) {
                    scheduleRetry(sender, dbxClient, file, dbxPath);
                    return; // Stop current call
                }

                // Finish
                finishChunkedUpload(dbxClient, file, dbxPath, uploaded, size, streamIn, cursor, progressListener);

                sendMessageWithLogs(MESSAGE_SUCCESSFUL_UPLOAD, sender);
                setProgress(file, uploaded, size, true);

                if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("CloudBackup.Options.DeleteLocalBackup")) {
                    tryDeleteFile(file);
                    ServerBackupPlugin.getPluginInstance().getLogger().info("File [" + file.getPath() + "] deleted.");
                }

                return; // success, stop retrying attempts
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void uploadFile(CommandSender sender, DbxClientV2 client, File file, String dbxPath) {
        setCurrentTask(addTask(TaskType.DROPBOX, TaskPurpose.UPLOAD, "Uploading " + file.getName()));

        try (InputStream streamIn = new FileInputStream(file.getPath())) {
            client.files().uploadBuilder(dbxPath + file.getName()).uploadAndFinish(streamIn);
        }

        catch (UploadErrorException e) {
            e.printStackTrace();
        }
        catch (DbxException | IOException e) {
            e.printStackTrace();
        }

        finally {
            sendMessageWithLogs(MESSAGE_SUCCESSFUL_UPLOAD, sender);
            removeTask(currentTask);

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
                chunkedUploadFile(sender, client, file, dbxPath);
            }
        }.runTaskLater(ServerBackupPlugin.getPluginInstance(), BASE_RETRY_DELAY_TICKS);
    }

    private Optional<StartResult> startChunkedUpload(DbxClientV2 dbxClient,
                                                     File file,
                                                     long uploaded,
                                                     long size,
                                                     InputStream inputStream) {
        String sessionId;
        long uploadedOut = uploaded;
        DbxException exception = null;

        try (UploadSessionStartUploader uploader = dbxClient.files().uploadSessionStart()) {
            sessionId = uploader.uploadAndFinish(inputStream).getSessionId();
        }

        catch (RetryException | NetworkIOException e) {
            exception = e;
            e.printStackTrace();
            return Optional.of(new StartResult(null, 0, exception));
        }

        catch (UploadSessionFinishErrorException e) {
            if (e.errorValue.isLookupFailed() && e.errorValue.getLookupFailedValue().isIncorrectOffset()) {
                exception = e;
                uploadedOut = e.errorValue
                        .getLookupFailedValue()
                        .getIncorrectOffsetValue()
                        .getCorrectOffset();
            } else {
                e.printStackTrace();
            }
            return Optional.of(new StartResult(null, uploadedOut, exception));
        }

        catch (DbxException | IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }

        uploadedOut += CHUNKED_UPLOAD_CHUNK_SIZE;
        setProgress(file, uploadedOut, size, false);
        return Optional.of(new StartResult(sessionId, uploadedOut, null));
    }

    private Optional<AppendResult> appendChunkedUpload(DbxClientV2 dbxClient,
                                                       File file,
                                                       String sessionId,
                                                       long uploaded,
                                                       long size,
                                                       InputStream inputStream,
                                                       UploadSessionCursor cursor) {
        UploadSessionCursor cursorOut = cursor;
        long uploadedOut = uploaded;
        DbxException exception = null;

        try (UploadSessionAppendV2Uploader uploader = dbxClient.files().uploadSessionAppendV2(cursorOut)) {
            uploader.uploadAndFinish(inputStream);
        }

        catch (RetryException | NetworkIOException e) {
            exception = e;
            e.printStackTrace();
            return Optional.of(new AppendResult(null, 0, exception));
        }

        catch (UploadSessionAppendErrorException e) {
            if (e.errorValue.isIncorrectOffset()) {
                exception = e;
                uploadedOut = e.errorValue
                        .getIncorrectOffsetValue()
                        .getCorrectOffset();
            } else {
                e.printStackTrace();
            }
            return Optional.of(new AppendResult(null, uploadedOut, exception));
        }

        catch (DbxException | IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }

        uploadedOut += CHUNKED_UPLOAD_CHUNK_SIZE;
        setProgress(file, uploadedOut, size, false);
        cursorOut = new UploadSessionCursor(sessionId, uploadedOut);
        return Optional.of(new AppendResult(cursorOut, uploadedOut, null));
    }

    private void finishChunkedUpload(DbxClientV2 dbxClient,
                                     File file,
                                     String dbxPath,
                                     long uploaded,
                                     long size,
                                     InputStream inputStream,
                                     UploadSessionCursor cursor,
                                     IOUtil.ProgressListener progressListener) {
        long remaining = size - uploaded;

        if (remaining < 0) {
            throw new IllegalStateException("Remaining bytes to upload cannot be negative. size=" + size + ", uploaded=" + uploaded);
        }

        CommitInfo commitInfo = CommitInfo.newBuilder(dbxPath)
                .withMode(WriteMode.ADD)
                .withClientModified(new Date(file.lastModified()))
                .build();

        try (UploadSessionFinishUploader uploader = dbxClient.files().uploadSessionFinish(cursor, commitInfo)) {
            uploader.uploadAndFinish(inputStream, remaining, progressListener);
        }

        catch (UploadSessionFinishErrorException e) {
            if (e.errorValue.isLookupFailed() && e.errorValue.getLookupFailedValue().isIncorrectOffset()) {
                long correctOffset = e.errorValue.getLookupFailedValue().getIncorrectOffsetValue().getCorrectOffset();
                ServerBackupPlugin.getPluginInstance().getLogger().warning("Dropbox: Incorrect offset, adjusting to " + correctOffset);
                finishChunkedUpload(dbxClient, file, dbxPath, correctOffset, size, inputStream, new UploadSessionCursor(cursor.getSessionId(), correctOffset), progressListener);
            } else {
                e.printStackTrace();
            }
        }

        catch (RetryException | NetworkIOException e) {
            e.printStackTrace();
        }

        catch (DbxException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void setCurrentTask(Task taskIn) {
        currentTask = taskIn;
    }
}
