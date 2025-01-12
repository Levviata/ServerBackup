package de.sebli.serverbackup.utils;

import com.dropbox.core.*;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.util.IOUtil;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.*;
import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.utils.records.AppendResult;
import de.sebli.serverbackup.utils.records.StartResult;
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
            credential = new DbxCredential("isly", 0L, Configuration.cloudInfo.getString(CLOUD_RT), appKey, secretKey);
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
                    Optional<StartResult> result = startChunkedUpload(dbxClient, file, size, uploaded, streamIn);

                    if (result.isPresent()) {
                        sessionId = result.get().sessionId();
                        uploaded = result.get().uploaded();
                    } else {
                        ServerBackupPlugin.getPluginInstance().getLogger().severe("Dropbox: Couldn't start chunked uploading to Dropbox because the result is missing!");
                    }
                }

                UploadSessionCursor cursor = new UploadSessionCursor(Objects.requireNonNull(sessionId), uploaded);

                // Append
                while ((size - uploaded) > CHUNKED_UPLOAD_CHUNK_SIZE) {
                    Optional<AppendResult> result = appendChunkedUpload(dbxClient, file, sessionId, uploaded, size, streamIn, cursor);

                    if (result.isPresent()) {
                        uploaded = result.get().uploaded();
                        cursor = result.get().cursor();
                    } else {
                        ServerBackupPlugin.getPluginInstance().getLogger().severe("Dropbox: Couldn't append chunked upload to Dropbox because the result is missing!");
                    }
                }

                // Finish
                finishChunkedUpload(dbxClient, file, dbxPath, uploaded, size, streamIn, cursor, progressListener);

                sender.sendMessage("Dropbox: Upload successfully. Backup stored on your dropbox account.");
                getProgress(file, uploaded, size, true);

                if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("CloudBackup.Options.DeleteLocalBackup")) {
                    tryDeleteFile(file);
                    System.out.println("File [" + file.getPath() + "] deleted.");
                }
                return;
            } catch (FileNotFoundException e) {
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

    private static Optional<StartResult> startChunkedUpload(DbxClientV2 dbxClient,
                                                            File file,
                                                            long uploadedIn,
                                                            long size,
                                                            InputStream inputStream) {
        String sessionId;
        long uploadedOut = uploadedIn;
        DbxException exception = null;

        try (UploadSessionStartUploader uploader = dbxClient.files().uploadSessionStart()) {
            sessionId = uploader.uploadAndFinish(inputStream).getSessionId();
        }

        // Catch specific exceptions first
        catch (RetryException | NetworkIOException e) {
            exception = e;
            e.printStackTrace();
            return Optional.of(new StartResult(null, 0, exception));
        }  catch (UploadSessionFinishErrorException e) {
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

        // Catch general exceptions
        catch (DbxException | IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }

        uploadedOut += CHUNKED_UPLOAD_CHUNK_SIZE;
        getProgress(file, uploadedOut, size, false);
        return Optional.of(new StartResult(sessionId, uploadedOut, null));
    }

    private static Optional<AppendResult> appendChunkedUpload(DbxClientV2 dbxClient,
                                                              File file,
                                                              String sessionIdIn,
                                                              long uploadedIn,
                                                              long size,
                                                              InputStream inputStream,
                                                              UploadSessionCursor cursorIn) {
        UploadSessionCursor cursorOut = cursorIn;
        long uploadedOut = uploadedIn;
        DbxException exception = null;

        try (InputStream streamIn = inputStream;
             UploadSessionAppendV2Uploader uploader = dbxClient.files().uploadSessionAppendV2(cursorOut)) {
            uploader.uploadAndFinish(streamIn);
        }

        // Catch specific exceptions first
        catch (RetryException | NetworkIOException e) {
            exception = e;
            e.printStackTrace();
            return Optional.of(new AppendResult(null, 0, exception));
        }  catch (UploadSessionAppendErrorException e) {
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


        // Catch general exceptions
        catch (DbxException | IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }

        uploadedOut += CHUNKED_UPLOAD_CHUNK_SIZE;
        getProgress(file, uploadedOut, size, false);
        cursorOut = new UploadSessionCursor(sessionIdIn, uploadedOut);
        return Optional.of(new AppendResult(cursorOut, uploadedOut, null));
    }

    private static Optional<Exception> finishChunkedUpload(DbxClientV2 dbxClient,
                                                    File file,
                                                    String dbxPath,
                                                    long uploadedIn,
                                                    long size,
                                                    InputStream inputStream,
                                                    UploadSessionCursor cursorIn,
                                                    IOUtil.ProgressListener progressListener) {
        long remaining = size - uploadedIn;

        CommitInfo commitInfo = CommitInfo.newBuilder(dbxPath)
                .withMode(WriteMode.ADD)
                .withClientModified(new Date(file.lastModified()))
                .build();
        try (UploadSessionFinishUploader uploader = dbxClient.files().uploadSessionFinish(cursorIn, commitInfo)) {
            uploader.uploadAndFinish(inputStream, remaining, progressListener);
        }

        // Catch specific exceptions first
        catch (RetryException | NetworkIOException e) {
            e.printStackTrace(); // TODO: Add specific logging logic
            return Optional.of(e);
        }

        // Catch general exceptions
        catch (DbxException | IOException e) {
            e.printStackTrace();
            return Optional.of(e);
        }

        return Optional.empty();
    }
}
