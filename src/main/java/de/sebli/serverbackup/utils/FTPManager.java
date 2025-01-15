package de.sebli.serverbackup.utils;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.utils.enums.TaskPurpose;
import de.sebli.serverbackup.utils.enums.TaskType;
import de.sebli.serverbackup.utils.records.Task;
import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static de.sebli.serverbackup.ServerBackupPlugin.sendMessageWithLogs;
import static de.sebli.serverbackup.utils.FileUtil.tryDeleteFile;
import static de.sebli.serverbackup.utils.GlobalConstants.FILE_NAME_PLACEHOLDER;
import static de.sebli.serverbackup.utils.TaskUtils.addTask;
import static de.sebli.serverbackup.utils.TaskUtils.removeTask;

public class FTPManager {

    private final CommandSender sender;

    private static final String SERVER_IP = ServerBackupPlugin.getPluginInstance().getConfig().getString("Ftp.Server.IP");
    private static final int SERVER_PORT = ServerBackupPlugin.getPluginInstance().getConfig().getInt("Ftp.Server.Port");
    private static final String SERVER_USER = ServerBackupPlugin.getPluginInstance().getConfig().getString("Ftp.Server.User");
    private static final String SERVER_PASSWORD = ServerBackupPlugin.getPluginInstance().getConfig().getString("Ftp.Server.Password");

    // TODO: Convert all literal string messages to constants
    private static final String ERROR_FTP_DOWNLOAD_FAILED = "Error.FtpDownloadFailed";
    private static final String ERROR_FTP_UPLOAD_FAILED = "Error.FtpUploadFailed";
    private static final String ERROR_FTP_NOT_FOUND = "Error.FtpNotFound";

    private static Task currentTask;

    public FTPManager(CommandSender sender) {
        this.sender = sender;
    }

    boolean isSSL = true;

    ServerBackupPlugin instance = ServerBackupPlugin.getPluginInstance();

    public void uploadFileToFTP(String filePath, boolean direct) {
        File file = new File(filePath);

        if (!file.getPath().contains(Configuration.backupDestination.replace("/", ""))) {
            file = Paths.get(Configuration.backupDestination, filePath).toFile();
        }

        if (!file.exists()) {
            sendMessageWithLogs(OperationHandler.processMessage("Error.NoBackupFound").replace(FILE_NAME_PLACEHOLDER, file.getName()), sender);

            return;
        }

        FTPSClient ftpsClient = new FTPSClient();
        FTPClient ftpClient = new FTPClient();

        try {
            if (isSSL) { // is FTPS
                handleUploadToFTPS(ftpsClient, file, direct);
            } else { // is NOT FTPS
                handleUploadToFTP(ftpClient, file);
            }
        }
        catch (IOException e) {
            sendMessageWithLogs(OperationHandler.processMessage(ERROR_FTP_UPLOAD_FAILED), sender);
            e.printStackTrace();
        }
        finally {
            disconnectClient(ftpsClient);
            disconnectClient(ftpClient);
        }
    }

    public void downloadFileFromFTP(String filePath) {
        File file = new File(filePath);

        FTPSClient ftpsClient = new FTPSClient();
        FTPClient ftpClient = new FTPClient();

        try {
            if (isSSL) {
                handleDownloadFromFTPS(ftpsClient, file);
            } else {
                handleDownloadFromFTP(ftpClient, file);
            }
        } catch (IOException e) {
            sendMessageWithLogs(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED), sender);
            e.printStackTrace();
        } finally {
            disconnectClient(ftpsClient);
            disconnectClient(ftpClient);
        }
    }

    public void deleteFile(String filePath) {
        File file = new File(filePath);

        FTPSClient ftpsClient = new FTPSClient();
        FTPClient ftpClient = new FTPClient();

        try {
            if (isSSL) {
                handleDeleteFileFTPS(ftpsClient, file);
            } else {
                handleDeleteFileFTP(ftpClient, file);
            }
        } catch (IOException e) {
            sendMessageWithLogs(OperationHandler.processMessage("Error.FtpDeletionFailed"), sender);
            e.printStackTrace();
        } finally {
            disconnectClient(ftpsClient);
            disconnectClient(ftpClient);
        }
    }

    public List<String> getFTPBackupList(boolean rawList) {
        List<String> backups = new ArrayList<>();

        FTPSClient ftpsClient = new FTPSClient();
        FTPClient ftpClient = new FTPClient();

        boolean useSSL = false;
        int attempts = 0;
        boolean success = false;

        while (attempts < 2 && !success) {
            try {
                if (useSSL) {
                    connectFTPorFTPS(ftpsClient);
                    backups = fetchBackupList(ftpsClient, rawList);
                } else {
                    connectFTPorFTPS(ftpClient);
                    backups = fetchBackupList(ftpClient, rawList);
                }
                success = true; // Mark success to exit the loop
            } catch (Exception e) {
                if (!useSSL) {
                    useSSL = true; // Switch to SSL for the next attempt
                } else {
                    // Log the error and exit
                    e.printStackTrace();
                    break;
                }
            } finally {
                disconnectClient(ftpsClient);
                disconnectClient(ftpClient);
            }
            attempts++; // Increment the attempts counter
        }

        return backups;
    }

    private List<String> fetchBackupList(FTPClient client, boolean rawList) throws IOException {
        List<String> backups = new ArrayList<>();
        FTPFile[] files = client.listFiles();

        int c = 1;
        for (FTPFile file : files) {
            double fileSize = (double) file.getSize() / 1000 / 1000;
            fileSize = Math.round(fileSize * 100.0) / 100.0;

            if (rawList) {
                backups.add(file.getName() + ":" + fileSize);
            } else {
                backups.add("§7[" + c + "]§f " + file.getName() + " §7[" + fileSize + "MB]");
            }

            c++;
        }

        return backups;
    }


    private void handleUploadToFTP(FTPClient client, File file) throws IOException {
        if (client instanceof FTPSClient) {
            throw new UnsupportedOperationException("Don't upload to FTP with a FTPS client! This is NOT supported and might cause security issues, use handleUploadToFTPS instead!");
        }

        connectFTPorFTPS(client);

        sendMessageWithLogs(OperationHandler.processMessage("Info.FtpUpload").replace(FILE_NAME_PLACEHOLDER, file.getName()), sender);

        setCurrentTask(addTask(TaskType.FTP, TaskPurpose.UPLOAD, "Uploading " + file.getPath() + " to FTP server"));

        boolean success = tryUploadFileToFTPorFTPS(client, file, false);

        if (success) {
            sendMessageWithLogs(OperationHandler.processMessage("Info.FtpUploadSuccess"), sender);

            if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("Ftp.DeleteLocalBackup")) {
                boolean exists = false;
                for (FTPFile backup : client.listFiles()) {
                    if (backup.getName().equalsIgnoreCase(file.getName()))
                        exists = true;
                }

                if (exists) {
                    tryDeleteFile(file);
                } else {
                    sendMessageWithLogs(OperationHandler.processMessage("Error.FtpLocalDeletionFailed"), sender);
                }
            }
        } else {
            sendMessageWithLogs(OperationHandler.processMessage(ERROR_FTP_UPLOAD_FAILED), sender);
        }

        removeTask(currentTask);
    }

    private void handleUploadToFTPS(FTPSClient client, File file, boolean direct) {
        try {
            connectFTPorFTPS(client);

            sendMessageWithLogs(OperationHandler.processMessage("Info.FtpUpload").replace(FILE_NAME_PLACEHOLDER, file.getName()), sender);

            setCurrentTask(addTask(TaskType.FTP, TaskPurpose.UPLOAD, "Uploading " + file.getPath() + " to FTPS server"));

            boolean isFileUploaded = tryUploadFileToFTPorFTPS(client, file, true);

            if (isFileUploaded) {
                sendMessageWithLogs(OperationHandler.processMessage("Info.FtpUploadSuccess"), sender);

                if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("Ftp.DeleteLocalBackup")) {
                    boolean exists = false;
                    for (FTPFile backup : client.listFiles()) {
                        if (backup.getName().equalsIgnoreCase(file.getName()))
                            exists = true;
                    }

                    if (exists) {
                        tryDeleteFile(file);
                    } else {
                        sendMessageWithLogs(OperationHandler.processMessage("Error.FtpLocalDeletionFailed"), sender);
                    }
                }
            } else {
                sendMessageWithLogs(OperationHandler.processMessage(ERROR_FTP_UPLOAD_FAILED), sender);
            }

            removeTask(currentTask);
        } catch (Exception e) {
            isSSL = false;
            uploadFileToFTP(file.getPath(), direct);
        }
    }

    private void handleDownloadFromFTP(FTPClient client, File file) throws IOException {
        if (client instanceof FTPSClient) {
            throw new UnsupportedOperationException("Don't download to FTP with a FTPS client! This is NOT supported and might cause security issues, use handleDownloadFromFTPS instead!");
        }

        connectFTPorFTPS(client);

        boolean exists = false;
        for (FTPFile backup : client.listFiles()) {
            if (backup.getName().equalsIgnoreCase(file.getName()))
                exists = true;
        }

        if (!exists) {
            sendMessageWithLogs(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()), sender);

            return;
        }

        sendMessageWithLogs(OperationHandler.processMessage("Info.FtpDownload").replace(FILE_NAME_PLACEHOLDER, file.getName()), sender);

        Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getPluginInstance(), () -> {
            File backupFile = Paths.get(Configuration.backupDestination, file.getPath()).toFile();

            try {
                FileUtils.copyFile(file, backupFile);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (backupFile.exists()) {
                tryDeleteFile(file);
            }
        });

        boolean success = tryDownloadFileFromFTPorFTPS(client, file);

        if (success) {
            sendMessageWithLogs(OperationHandler.processMessage("Info.FtpDownloadSuccess"), sender);
        } else {
            sendMessageWithLogs(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED), sender);
        }
    }

    private void handleDownloadFromFTPS(FTPSClient client, File file) {
        try {
            connectFTPorFTPS(client);

            boolean exists = false;
            for (FTPFile backup : client.listFiles()) {
                if (backup.getName().equalsIgnoreCase(file.getName()))
                    exists = true;
            }

            if (!exists) {
                sendMessageWithLogs(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()), sender);

                return;
            }

            sendMessageWithLogs(OperationHandler.processMessage("Info.FtpDownload").replace(FILE_NAME_PLACEHOLDER, file.getName()), sender);

            Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getPluginInstance(), () -> {
                File backupFile = Paths.get(Configuration.backupDestination, file.getPath()).toFile();

                try {
                    FileUtils.copyFile(file, backupFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (backupFile.exists()) {
                    tryDeleteFile(file);
                }
            });

            boolean success = tryDownloadFileFromFTPorFTPS(client, file);

            if (success) {
                sendMessageWithLogs(OperationHandler.processMessage("Info.FtpDownloadSuccess"), sender);
            } else {
                sendMessageWithLogs(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED), sender);
            }
        } catch (Exception e) {
            isSSL = false;
            downloadFileFromFTP(file.getPath());
        }
    }

    private void handleDeleteFileFTP(FTPClient client, File file) throws IOException {
        if (client instanceof FTPSClient) {
            throw new UnsupportedOperationException("Don't delete to FTP with a FTPS client! This is NOT supported and might cause security issues, use handleDeleteFTPS instead!");
        }

        connectFTPorFTPS(client);

        boolean exists = false;
        for (FTPFile backup : client.listFiles()) {
            if (backup.getName().equalsIgnoreCase(file.getName()))
                exists = true;
        }

        if (!exists) {
            sendMessageWithLogs(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()), sender);

            return;
        }

        sendMessageWithLogs(OperationHandler.processMessage("Info.FtpDeletion").replace(FILE_NAME_PLACEHOLDER, file.getName()), sender);

        boolean success = tryDeleteFile(file);

        if (success) {
            sendMessageWithLogs(OperationHandler.processMessage("Info.FtpDeletionSuccess"), sender);
        } else {
            sendMessageWithLogs(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED), sender);
        }
    }

    private void handleDeleteFileFTPS(FTPSClient client, File file) {
        try {
            connectFTPorFTPS(client);

            boolean exists = false;
            for (FTPFile backup : client.listFiles()) {
                if (backup.getName().equalsIgnoreCase(file.getName()))
                    exists = true;
            }

            if (!exists) {
                sendMessageWithLogs(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()), sender);

                return;
            }

            sendMessageWithLogs(OperationHandler.processMessage("Info.FtpDeletion").replace(FILE_NAME_PLACEHOLDER, file.getName()), sender);

            boolean success = tryDeleteFile(file);

            if (success) {
                sendMessageWithLogs(OperationHandler.processMessage("Info.FtpDeletionSuccess"), sender);
            } else {
                sendMessageWithLogs(OperationHandler.processMessage("Error.FtpDeletionFailed"), sender);
            }
        } catch (Exception e) {
            isSSL = false;
            deleteFile(file.getPath());
        }
    }

    /**
     * Connects and configures an FTP or FTPS client.
     * <p>
     * This method establishes a connection to an FTP/FTPS server, logs in with the provided
     * credentials, sets the client to passive mode, and configures file transfer settings.
     * If the provided client is an instance of {@link org.apache.commons.net.ftp.FTPSClient},
     * additional security commands (PBSZ and PROT) are executed to secure the connection.
     *
     * @param client the {@link org.apache.commons.net.ftp.FTPClient} instance to connect.
     *               Can be either FTPClient or FTPSClient.
     * @throws IOException if an error occurs during the connection or configuration process.
     */
    private void connectFTPorFTPS(FTPClient client) throws IOException {
        client.connect(SERVER_IP, SERVER_PORT);
        client.login(SERVER_USER, SERVER_PASSWORD);
        client.enterLocalPassiveMode();

        if (client instanceof FTPSClient clientFTPS) {
            clientFTPS.execPBSZ(0);
            clientFTPS.execPROT("P");
        }

        client.setFileType(FTP.BINARY_FILE_TYPE);
        client.setFileTransferMode(FTP.BINARY_FILE_TYPE);

        client.changeWorkingDirectory(
                ServerBackupPlugin.getPluginInstance().getConfig().getString("Ftp.Server.BackupDirectory"));
    }

    /**
     * Safely disconnects an FTP/FTPS client from the server.
     * <p>
     * This method logs out the client and closes the connection if the client is currently connected.
     * Any {@link IOException} encountered during the process is caught and printed to the error stream.
     *
     * @param client the {@link org.apache.commons.net.ftp.FTPClient} instance to disconnect (instanceof FTPClient are also acceptable. e.i: FTPS).
     */
    private void disconnectClient(FTPClient client) {
        try {
            if (client.isConnected()) {
                client.logout();
                client.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean tryUploadFileToFTPorFTPS(FTPClient client, File file, boolean isSSL) {
        try (InputStream inputStream = new FileInputStream(file)) {
            if (isSSL) {
                // DEBUG
                Bukkit.getLogger().info("(BETA) FTPS-DEBUG INFO: " + client.getReplyString());
                Bukkit.getLogger().info(
                        "Use this info for reporting FTPS related bugs. Ignore if everything is fine.");
            } else {
                // DEBUG
                Bukkit.getLogger().info("(BETA) FTP-DEBUG INFO: " + client.getReplyString());
                Bukkit.getLogger().info(
                        "Use this info for reporting FTP related bugs. Ignore if everything is fine.");
            }
            return client.storeFile(file.getName(), inputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean tryDownloadFileFromFTPorFTPS(FTPClient client, File file) {
        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file))) {
            return client.retrieveFile(file.getName(), outputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void setCurrentTask(Task taskIn) {
        currentTask = taskIn;
    }
}
