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

public class FtpManager {

    private final CommandSender sender;

    private static final String SERVER_IP = ServerBackupPlugin.getInstance().getConfig().getString("Ftp.Server.IP");
    private static final int SERVER_PORT = ServerBackupPlugin.getInstance().getConfig().getInt("Ftp.Server.Port");
    private static final String SERVER_USER = ServerBackupPlugin.getInstance().getConfig().getString("Ftp.Server.User");
    private static final String SERVER_PASSWORD = ServerBackupPlugin.getInstance().getConfig().getString("Ftp.Server.Password");

    // TODO: Convert all literal string errors to constants
    private static final String ERROR_FTP_DOWNLOAD_FAILED = "Error.FtpDownloadFailed";
    private static final String ERROR_FTP_UPLOAD_FAILED = "Error.FtpUploadFailed";
    private static final String ERROR_FTP_NOT_FOUND = "Error.FtpNotFound";

    public FtpManager(CommandSender sender) {
        this.sender = sender;
    }

    boolean isSSL = true;

    ServerBackupPlugin backup = ServerBackupPlugin.getInstance();

    public void uploadFileToFtp(String filePath, boolean direct) {
        File file = new File(filePath);

        if (!file.getPath().contains(Configuration.backupDestination.replace("/", ""))) {
            file = new File(Configuration.backupDestination + "//" + filePath);
            filePath = file.getPath();
        }

        if (!file.exists()) {
            sender.sendMessage(OperationHandler.processMessage("Error.NoBackupFound").replace(FILE_NAME_PLACEHOLDER, file.getName()));

            return;
        }

        FTPSClient ftpsClient = new FTPSClient();
        FTPClient ftpClient = new FTPClient();

        try {
            if (!isSSL) {
                connect(ftpClient);

                sender.sendMessage(OperationHandler.processMessage("Info.FtpUpload").replace(FILE_NAME_PLACEHOLDER, file.getName()));
                OperationHandler.tasks.add("FTP UPLOAD {" + filePath + "}"); // wont comply to java:S1192, we are never refactoring this

                InputStream inputStream = new FileInputStream(file);

                boolean done = ftpClient.storeFile(file.getName(), inputStream);

                // DEBUG
                Bukkit.getLogger().log(Level.INFO, "(BETA) FTP-DEBUG INFO: " + ftpClient.getReplyString());
                Bukkit.getLogger().log(Level.INFO,
                        "Use this info for reporting ftp related bugs. Ignore it if everything is fine.");

                inputStream.close();

                if (done) {
                    sender.sendMessage(OperationHandler.processMessage("Info.FtpUploadSuccess"));

                    if (ServerBackupPlugin.getInstance().getConfig().getBoolean("Ftp.DeleteLocalBackup")) {
                        boolean exists = false;
                        for (FTPFile backup : ftpClient.listFiles()) {
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

                OperationHandler.tasks.remove("FTP UPLOAD {" + filePath + "}");
            } else {
                try {
                    connect(ftpsClient);

                    InputStream inputStream = new FileInputStream(file);

                    boolean done = ftpsClient.storeFile(file.getName(), inputStream);

                    // DEBUG
                    Bukkit.getLogger().log(Level.INFO, "(BETA) FTPS-DEBUG INFO: " + ftpsClient.getReplyString());
                    Bukkit.getLogger().log(Level.INFO,
                            "Use this info for reporting ftp related bugs. Ignore it if everything is fine.");

                    inputStream.close();

                    if (done) {
                        sender.sendMessage(OperationHandler.processMessage("Info.FtpUploadSuccess"));

                        if (ServerBackupPlugin.getInstance().getConfig().getBoolean("Ftp.DeleteLocalBackup")) {
                            boolean exists = false;
                            for (FTPFile backup : ftpsClient.listFiles()) {
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

                    if (OperationHandler.tasks.contains("FTP UPLOAD {" + filePath + "}")) {
                        OperationHandler.tasks.remove("FTP UPLOAD {" + filePath + "}");
                    }
                } catch (Exception e) {
                    isSSL = false;
                    uploadFileToFtp(filePath, direct);
                }
            }
        } catch (IOException e) {
            sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_UPLOAD_FAILED));
            e.printStackTrace();
        } finally {
            disconnect(ftpsClient);
            disconnect(ftpClient);
        }
    }

    public void downloadFileFromFtp(String filePath) {
        File file = new File(filePath);

        FTPSClient ftpsClient = new FTPSClient();
        FTPClient ftpClient = new FTPClient();

        try {
            if (!isSSL) {
                connect(ftpClient);

                boolean exists = false;
                for (FTPFile backup : ftpClient.listFiles()) {
                    if (backup.getName().equalsIgnoreCase(file.getName()))
                        exists = true;
                }

                if (!exists) {
                    sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()));

                    return;
                }

                sender.sendMessage(OperationHandler.processMessage("Info.FtpDownload").replace(FILE_NAME_PLACEHOLDER, file.getName()));

                OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
                boolean success = ftpClient.retrieveFile(file.getName(), outputStream);
                outputStream.close();

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

                if (success) {
                    sender.sendMessage(OperationHandler.processMessage("Info.FtpDownloadSuccess"));
                } else {
                    sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED));
                }
            } else {
                try {
                    connect(ftpsClient);

                    boolean exists = false;
                    for (FTPFile backup : ftpsClient.listFiles()) {
                        if (backup.getName().equalsIgnoreCase(file.getName()))
                            exists = true;
                    }

                    if (!exists) {
                        sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()));

                        return;
                    }

                    sender.sendMessage(OperationHandler.processMessage("Info.FtpDownload").replace(FILE_NAME_PLACEHOLDER, file.getName()));

                    OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
                    boolean success = ftpsClient.retrieveFile(file.getName(), outputStream);
                    outputStream.close();

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

                    if (success) {
                        sender.sendMessage(OperationHandler.processMessage("Info.FtpDownloadSuccess"));
                    } else {
                        sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED));
                    }
                } catch (Exception e) {
                    isSSL = false;
                    downloadFileFromFtp(filePath);
                }
            }
        } catch (IOException e) {
            sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED));
            e.printStackTrace();
        } finally {
            disconnect(ftpsClient);
            disconnect(ftpClient);
        }
    }

    public void deleteFile(String filePath) {
        File file = new File(filePath);

        FTPSClient ftpsClient = new FTPSClient();
        FTPClient ftpClient = new FTPClient();

        try {
            if (!isSSL) {
                connect(ftpClient);

                boolean exists = false;
                for (FTPFile backup : ftpClient.listFiles()) {
                    if (backup.getName().equalsIgnoreCase(file.getName()))
                        exists = true;
                }

                if (!exists) {
                    sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()));

                    return;
                }

                sender.sendMessage(OperationHandler.processMessage("Info.FtpDeletion").replace(FILE_NAME_PLACEHOLDER, file.getName()));

                boolean success = ftpClient.deleteFile(file.getPath());

                if (success) {
                    sender.sendMessage(OperationHandler.processMessage("Info.FtpDeletionSuccess"));
                } else {
                    sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_DOWNLOAD_FAILED));
                }
            } else {
                try {
                    connect(ftpsClient);

                    boolean exists = false;
                    for (FTPFile backup : ftpsClient.listFiles()) {
                        if (backup.getName().equalsIgnoreCase(file.getName()))
                            exists = true;
                    }

                    if (!exists) {
                        sender.sendMessage(OperationHandler.processMessage(ERROR_FTP_NOT_FOUND).replace(FILE_NAME_PLACEHOLDER, file.getName()));

                        return;
                    }

                    sender.sendMessage(OperationHandler.processMessage("Info.FtpDeletion").replace(FILE_NAME_PLACEHOLDER, file.getName()));

                    boolean success = ftpsClient.deleteFile(file.getPath());

                    if (success) {
                        sender.sendMessage(OperationHandler.processMessage("Info.FtpDeletionSuccess"));
                    } else {
                        sender.sendMessage(OperationHandler.processMessage("Error.FtpDeletionFailed"));
                    }
                } catch (Exception e) {
                    isSSL = false;
                    deleteFile(filePath);
                }
            }
        } catch (IOException e) {
            sender.sendMessage(OperationHandler.processMessage("Error.FtpDeletionFailed"));
            e.printStackTrace();
        } finally {
            disconnect(ftpsClient);
            disconnect(ftpClient);
        }
    }

    public List<String> getFtpBackupList(boolean rawList) {
        List<String> backups = new ArrayList<>();

        FTPSClient ftpsClient = new FTPSClient();
        FTPClient ftpClient = new FTPClient();

        try {
            connect(ftpClient);

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
            getFtpBackupList(rawList);

            try {
                connect(ftpsClient);

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
                getFtpBackupList(rawList);
            }
        } finally {
            disconnect(ftpsClient);
            disconnect(ftpClient);

            return backups;
        }
    }

    private void connect(FTPClient client) throws IOException {
        client.connect(SERVER_IP, SERVER_PORT);
        client.login(SERVER_USER, SERVER_PASSWORD);
        client.enterLocalPassiveMode();

        client.setFileType(FTP.BINARY_FILE_TYPE);
        client.setFileTransferMode(FTP.BINARY_FILE_TYPE);

        client.changeWorkingDirectory(
                ServerBackupPlugin.getInstance().getConfig().getString("Ftp.Server.BackupDirectory"));
    }

    private void connect(FTPSClient client) throws IOException {
        client.connect(SERVER_IP, SERVER_PORT);
        client.login(SERVER_USER, SERVER_PASSWORD);
        client.enterLocalPassiveMode();

        client.execPBSZ(0);
        client.execPROT("P");

        client.setFileType(FTP.BINARY_FILE_TYPE);
        client.setFileTransferMode(FTP.BINARY_FILE_TYPE);

        client.changeWorkingDirectory(
                ServerBackupPlugin.getInstance().getConfig().getString("Ftp.Server.BackupDirectory"));
    }

    private void disconnect(FTPClient client) {
        try {
            if (client.isConnected()) {
                client.logout();
                client.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void disconnect(FTPSClient client) {
        try {
            if (client.isConnected()) {
                client.logout();
                client.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
