package de.sebli.serverbackup.utils;

import com.dropbox.core.*;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.util.IOUtil;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.*;
import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.OperationHandler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.*;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static de.sebli.serverbackup.utils.FileUtil.tryDeleteFile;

public class DropboxManager {

    private CommandSender sender;
    public DropboxManager(CommandSender sender) {
        this.sender = sender;
    }

    private static final String ACCESS_TOKEN = Configuration.cloudInfo.getString("Cloud.Dropbox.AccessToken");
    private static final String CLOUD_RT = "Cloud.Dropbox.RT";

    public void uploadToDropbox(String filePath) {
        File file = new File(filePath);

        if (!file.getPath().contains(Configuration.backupDestination.replace("/", ""))) {
            file = new File(Configuration.backupDestination + "//" + filePath);
        }

        if (!file.exists()) {
            sender.sendMessage("Dropbox: Backup '" + file.getName() + "' not found.");
            return;
        }

        DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/ServerBackupG").build();

        String appKey = Configuration.cloudInfo.getString("Cloud.Dropbox.AppKey");
        String secretKey = Configuration.cloudInfo.getString("Cloud.Dropbox.AppSecret");

        DbxClientV2 client = null;
        DbxCredential credential = null;

        if (!Configuration.cloudInfo.contains(CLOUD_RT)) {
            DbxAppInfo appInfo = new DbxAppInfo(appKey, secretKey);
            DbxWebAuth webAuth = new DbxWebAuth(config, appInfo);
            DbxAuthFinish authFinish = null;
            try {
                authFinish = webAuth.finishFromCode(ACCESS_TOKEN);
            } catch (DbxException e) {
                ServerBackupPlugin.getPluginInstance().getLogger().warning(MessageFormat.format("Error during Dropbox authentication process: {0}", e.getMessage()));
            }
            credential = new DbxCredential(authFinish.getAccessToken(), 60L, authFinish.getRefreshToken(), appKey, secretKey);

            Configuration.cloudInfo.set(CLOUD_RT, authFinish.getRefreshToken());
            Configuration.saveCloud();
        } else {
            credential = new DbxCredential(ServerBackupPlugin.getPluginInstance().getEnvKey().get("DROPBOX_KEY"), 0L, Configuration.cloudInfo.getString(CLOUD_RT), appKey, secretKey);
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

    private static final long CHUNKED_UPLOAD_CHUNK_SIZE = 16L << 20;
    private static final int CHUNKED_UPLOAD_MAX_ATTEMPTS = 5;

    static String lastProgress = "";

    private static void chunkedUploadFile(CommandSender sender, DbxClientV2 dbxClient, File file, String dbxPath) {
        long size = file.length();

        long uploaded = 0L;
        DbxException thrown = null;

        IOUtil.ProgressListener progressListener = new IOUtil.ProgressListener() {
            long uploadedBytes = 0;

            @Override
            public void onProgress(long l) {
                getProgress(file, l + uploadedBytes, size, false);
                if (l == CHUNKED_UPLOAD_CHUNK_SIZE) uploadedBytes += CHUNKED_UPLOAD_CHUNK_SIZE;
            }
        };

        String sessionId = null;
        for (int attemptNumber = 0; attemptNumber < CHUNKED_UPLOAD_MAX_ATTEMPTS; ++attemptNumber) {
            if (attemptNumber > 0) {
                Bukkit.getLogger().info(
                        MessageFormat.format(
                                "Dropbox: Chunk upload failed. Retrying ({0}/{1})...",
                                attemptNumber + 1,
                                CHUNKED_UPLOAD_MAX_ATTEMPTS
                        )
                );
            }

            try (InputStream streamIn = new FileInputStream(file)) {
                long bytesSkipped = streamIn.skip(uploaded);

                if (bytesSkipped < uploaded) {
                    ServerBackupPlugin.getPluginInstance().getLogger().warning(
                            MessageFormat.format(
                                    "Dropbox: Only skipped {0} bytes out of a total of {1}, this will likely cause issues!",
                                    bytesSkipped,
                                    uploaded
                            ));
                }

                // Start
                if (sessionId == null) {
                    sessionId = dbxClient.files().uploadSessionStart()
                            .uploadAndFinish(in)
                            .getSessionId();
                    uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                    getProgress(file.getName(), uploaded, size, false);
                }

                UploadSessionCursor cursor = new UploadSessionCursor(Objects.requireNonNull(sessionId), uploaded);

                // Append
                while ((size - uploaded) > CHUNKED_UPLOAD_CHUNK_SIZE) {
                    dbxClient.files().uploadSessionAppendV2(cursor)
                            .uploadAndFinish(in);
                    uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                    getProgress(file.getName(), uploaded, size, false);
                    cursor = new UploadSessionCursor(sessionId, uploaded);
                }

                // Finish
                long remaining = size - uploaded;
                CommitInfo commitInfo = CommitInfo.newBuilder(dbxPath)
                        .withMode(WriteMode.ADD)
                        .withClientModified(new Date(file.lastModified()))
                        .build();
                dbxClient.files().uploadSessionFinish(cursor, commitInfo).uploadAndFinish(in, remaining, progressListener);

                sender.sendMessage("Dropbox: Upload successfully. Backup stored on your dropbox account.");
                getProgress(file, uploaded, size, true);

                if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("CloudBackup.Options.DeleteLocalBackup")) {
                    tryDeleteFile(file);
                    System.out.println("File [" + file.getPath() + "] deleted.");
                }
                return;
            } catch (RetryException e) {
                thrown = e;
            } catch (NetworkIOException e) {
                thrown = e;
            } catch (UploadSessionFinishErrorException e) {
                if (e.errorValue.isLookupFailed() && e.errorValue.getLookupFailedValue().isIncorrectOffset()) {
                    thrown = e;
                    uploaded = e.errorValue
                            .getLookupFailedValue()
                            .getIncorrectOffsetValue()
                            .getCorrectOffset();
                } else {
                    e.printStackTrace();
                    return;
                }
            } catch (DbxException e) {
                e.printStackTrace();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        Bukkit.getLogger().warning( "Dropbox: Too many upload attempts. Check your server's connection and try again." + "\n" + thrown.getMessage());
    }

    private static void uploadFile(CommandSender sender, DbxClientV2 client, File file, String dbxPath) {
        OperationHandler.getTasks().add("DROPBOX UPLOAD {" + file.getName() + "}"); // wont comply to java:S1192, we are never refactoring this

        try (InputStream in = new FileInputStream(file.getPath())) {
            client.files().uploadBuilder(dbxPath + file.getName()).uploadAndFinish(in);
        }

        catch (UploadErrorException e) {
            e.printStackTrace();
        }
        catch (DbxException | IOException e) {
            e.printStackTrace();
        }

        finally {
            sender.sendMessage("Dropbox: Upload successfully. Backup stored on your dropbox account.");
            OperationHandler.getTasks().remove("DROPBOX UPLOAD {" + file.getName() + "}"); // same as line 178

            if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("CloudBackup.Options.DeleteLocalBackup")) {
                tryDeleteFile(file);
                System.out.println("File [" + file.getPath() + "] deleted.");
            }
        }
    }

    private static String getProgress(File file, long uploaded, long size, boolean finished) {
        if (!lastProgress.isEmpty()) {
            OperationHandler.getTasks().remove(lastProgress);
        }

        if (!finished) {
            String progress = "DROPBOX UPLOAD {" + file.getName() + ", Progress: " + Math.round((uploaded / (double) size) * 100) + "%}"; // same as line 178
            OperationHandler.getTasks().add(progress);
            lastProgress = progress;

            return progress;
        }

        return "DROPBOX UPLOAD {" + file.getName() + ", Progress: finished}"; // same as line 178
    }

}
