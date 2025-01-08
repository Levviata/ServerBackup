package de.sebli.serverbackup.utils;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.OperationHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static de.sebli.serverbackup.utils.GlobalConstants.FILE_NAME_PLACEHOLDER;

public class FTPManager {

    private final CommandSender sender;

    private static final String SERVER_IP = ServerBackupPlugin.getInstance().getConfig().getString("Ftp.Server.IP");
    private static final int SERVER_PORT = ServerBackupPlugin.getInstance().getConfig().getInt("Ftp.Server.Port");
    private static final String SERVER_USER = ServerBackupPlugin.getInstance().getConfig().getString("Ftp.Server.User");
    private static final String SERVER_PASSWORD = ServerBackupPlugin.getInstance().getConfig().getString("Ftp.Server.Password");

    // TODO: Convert all literal string messages to constants
    private static final String ERROR_FTP_DOWNLOAD_FAILED = "Error.FtpDownloadFailed";
    private static final String ERROR_FTP_UPLOAD_FAILED = "Error.FtpUploadFailed";
    private static final String ERROR_FTP_NOT_FOUND = "Error.FtpNotFound";

    public FTPManager(CommandSender sender) {
        this.sender = sender;
    }

    boolean isSSL = true;

    ServerBackupPlugin instance = ServerBackupPlugin.getInstance();

    public void uploadFileToFTP(String filePath, boolean direct) { // OBJECTIVES: reduce complexity and move parts of code to their own methods
        File file = new File(filePath);

        if (!file.getPath().contains(Configuration.backupDestination.replace("/", ""))) {
            file = new File(Configuration.backupDestination + "//" + filePath);
        }

        if (!file.exists()) {
            sender.sendMessage(OperationHandler.processMessage("Error.NoBackupFound").replace(FILE_NAME_PLACEHOLDER, file.getName()));

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
            sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_UPLOAD_FAILED));
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
            sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED));
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
            sender.sendMessage(OperationHandler.processMessage("Error.FtpDeletionFailed"));
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

        try {
            connectFTPorFTPS(ftpClient);

            FTPFile[] files = ftpClient.listFiles();

            int c = 1;

            for (FTPFile file : files) {
                double fileSize = (double) file.getSize() / 1000 / 1000;
                fileSize = Math.round(fileSize * 100.0) / 100.0;

                if (rawList) {
                    backups.add(file.getName() + ":" + file.getSize());
                } else {
                    backups.add("§7[" + c + "]§f " + file.getName() + " §7[" + fileSize + "MB]");
                }

                c++;
            }
        } catch (Exception e) {
            isSSL = true;
            getFTPBackupList(rawList);

            try {
                connectFTPorFTPS(ftpsClient);

                FTPFile[] files = ftpsClient.listFiles();

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
            } catch (Exception ex) {
                isSSL = false;
                getFTPBackupList(rawList);
            }
        } finally {
            disconnectClient(ftpsClient);
            disconnectClient(ftpClient);

            return backups;
        }
    }

    private void handleUploadToFTP(FTPClient client, File file) throws IOException {
        if (client instanceof FTPSClient) {
            throw new UnsupportedOperationException("Don't upload to FTP with a FTPS client! This is NOT supported and might cause security issues, use handleUploadToFTPS instead!");
        }

        connectFTPorFTPS(client);

        sender.sendMessage(OperationHandler.processMessage("Info.FtpUpload").replace(FILE_NAME_PLACEHOLDER, file.getName()));
        OperationHandler.tasks.add("FTP UPLOAD {" + file.getPath() + "}"); // wont comply to java:S1192, we are never refactoring this

        InputStream inputStream = new FileInputStream(file);

        boolean done = client.storeFile(file.getName(), inputStream);

        // DEBUG
        Bukkit.getLogger().info("(BETA) FTP-DEBUG INFO: " + client.getReplyString());
        Bukkit.getLogger().info(
                "Use this info for reporting ftp related bugs. Ignore it if everything is fine.");

        inputStream.close();

        if (done) {
            sender.sendMessage(OperationHandler.processMessage("Info.FtpUploadSuccess"));

            if (ServerBackupPlugin.getInstance().getConfig().getBoolean("Ftp.DeleteLocalBackup")) {
                boolean exists = false;
                for (FTPFile backup : client.listFiles()) {
                    if (backup.getName().equalsIgnoreCase(file.getName()))
                        exists = true;
                }

                if (exists) {
                    file.delete();
                } else {
                    sender.sendMessage(OperationHandler.processMessage("Error.FtpLocalDeletionFailed"));
                }
            }
        } else {
            sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_UPLOAD_FAILED));
        }

        OperationHandler.tasks.remove("FTP UPLOAD {" + file.getPath() + "}");
    }

    private void handleUploadToFTPS(FTPSClient client, File file, boolean direct) {
        try {
            connectFTPorFTPS(client);

            boolean isFileUploaded = tryUploadFileToFTPorFTPS(client, file);

            if (isFileUploaded) {
                sender.sendMessage(OperationHandler.processMessage("Info.FtpUploadSuccess"));

                if (ServerBackupPlugin.getInstance().getConfig().getBoolean("Ftp.DeleteLocalBackup")) {
                    boolean exists = false;
                    for (FTPFile backup : client.listFiles()) {
                        if (backup.getName().equalsIgnoreCase(file.getName()))
                            exists = true;
                    }

                    if (exists) {
                        file.delete();
                    } else {
                        sender.sendMessage(OperationHandler.processMessage("Error.FtpLocalDeletionFailed"));
                    }
                }
            } else {
                sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_UPLOAD_FAILED));
            }

            if (OperationHandler.tasks.contains("FTP UPLOAD {" + file.getPath() + "}")) {
                OperationHandler.tasks.remove("FTP UPLOAD {" + file.getPath() + "}");
            }
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
            sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()));

            return;
        }

        sender.sendMessage(OperationHandler.processMessage("Info.FtpDownload").replace(FILE_NAME_PLACEHOLDER, file.getName()));

        boolean isSuccessfulDownload = tryDownloadFileFromFTPorFTPS(client, file);

        Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getInstance(), () -> {
            File dFile = new File(Configuration.backupDestination + "//" + file.getPath());

            try {
                FileUtils.copyFile(file, dFile);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (dFile.exists()) {
                file.delete();
            }
        });

        if (isSuccessfulDownload) {
            sender.sendMessage(OperationHandler.processMessage("Info.FtpDownloadSuccess"));
        } else {
            sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED));
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
                sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()));

                return;
            }

            sender.sendMessage(OperationHandler.processMessage("Info.FtpDownload").replace(FILE_NAME_PLACEHOLDER, file.getName()));

            boolean isSuccessfulDownload = tryDownloadFileFromFTPorFTPS(client, file);

            Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getInstance(), () -> {
                File backupFile = new File(Configuration.backupDestination + "//" + file.getPath());

                try {
                    FileUtils.copyFile(file, backupFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (backupFile.exists()) {
                    file.delete();
                }
            });

            if (isSuccessfulDownload) {
                sender.sendMessage(OperationHandler.processMessage("Info.FtpDownloadSuccess"));
            } else {
                sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED));
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
            sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()));

            return;
        }

        sender.sendMessage(OperationHandler.processMessage("Info.FtpDeletion").replace(FILE_NAME_PLACEHOLDER, file.getName()));

        boolean success = client.deleteFile(file.getPath());

        if (success) {
            sender.sendMessage(OperationHandler.processMessage("Info.FtpDeletionSuccess"));
        } else {
            sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED));
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
                sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()));

                return;
            }

            sender.sendMessage(OperationHandler.processMessage("Info.FtpDeletion").replace(FILE_NAME_PLACEHOLDER, file.getName()));

            boolean success = client.deleteFile(file.getPath());

            if (success) {
                sender.sendMessage(OperationHandler.processMessage("Info.FtpDeletionSuccess"));
            } else {
                sender.sendMessage(OperationHandler.processMessage("Error.FtpDeletionFailed"));
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
                ServerBackupPlugin.getInstance().getConfig().getString("Ftp.Server.BackupDirectory"));
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

    private boolean tryUploadFileToFTPorFTPS(FTPClient client, File file) {
        try (InputStream inputStream = new FileInputStream(file)) {
            // DEBUG
            Bukkit.getLogger().info("(BETA) FTPS-DEBUG INFO: " + client.getReplyString());
            Bukkit.getLogger().info(
                    "Use this info for reporting ftp related bugs. Ignore it if everything is fine.");

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
}
