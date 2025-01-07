package de.sebli.serverbackup.core;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.utils.DropboxManager;
import de.sebli.serverbackup.utils.FTPManager;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static de.sebli.serverbackup.utils.GlobalConstants.CONFIG_BACKUP_DESTINATION;
import static de.sebli.serverbackup.utils.GlobalConstants.FILE_NAME_PLACEHOLDER;

public class ZipManager {

    private final String sourceFilePath;
    private String targetFilePath;
    private final CommandSender sender;
    private final boolean sendDebugMessage;
    private final boolean isSaving;
    private final boolean isFullBackup;

    private static boolean isCommandTimerRunning = false;
    private static String ERROR_ZIPPING = "Error while zipping files.";
    private static String PATH_COMMAND_AFTER_AUTOMATIC_BACKUP = "CommandAfterAutomaticBackup";

    public ZipManager(String sourceFilePath, String targetFilePath, CommandSender sender, boolean sendDebugMessage,
                      boolean isSaving, boolean isFullBackup) {
        this.sourceFilePath = sourceFilePath;
        this.targetFilePath = targetFilePath;
        this.sender = sender;
        this.sendDebugMessage = sendDebugMessage;
        this.isSaving = isSaving;
        this.isFullBackup = isFullBackup;
    }

    public void zip() throws IOException { // cognitive complexity of 123, gg
        Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getInstance(), () -> {
            long sTime = System.nanoTime();

            Bukkit.getLogger().log(Level.INFO, "");
            Bukkit.getLogger().log(Level.INFO, "ServerBackup | Start zipping...");
            Bukkit.getLogger().log(Level.INFO, "");

            Path p;
            try {
                p = Files.createFile(Paths.get(targetFilePath));
            } catch (IOException e) {
                e.printStackTrace();
                Bukkit.getLogger().log(Level.WARNING, ERROR_ZIPPING);
                return;
            }

            try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p))) {
                Path pp = Paths.get(sourceFilePath);
                Files.walk(pp).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                    if (!path.toString().contains(ServerBackupPlugin.getInstance().getConfig().getString(CONFIG_BACKUP_DESTINATION)
                            .replace("/", "")) || !isSaving) {
                        ZipEntry zipEntry = new ZipEntry(pp.relativize(path).toString());

                        for (String blacklist : ServerBackupPlugin.getInstance().getConfig().getStringList("Blacklist")) {
                            File bl = new File(blacklist);

                            if (bl.isDirectory()) {
                                if (path.toFile().getParent().toString().startsWith(bl.toString())
                                        || path.toFile().getParent().toString().startsWith(".\\" + bl.toString())) {
                                    return;
                                }
                            } else {
                                if (path.equals(new File(blacklist).toPath())
                                        || path.equals(new File(".\\" + blacklist).toPath())) {
                                    sender.sendMessage("Found '" + path + "' in blacklist. Skipping file.");
                                    return;
                                }
                            }
                        }

                        if (!isFullBackup) {
                            if (ServerBackupPlugin.getInstance().getConfig().getBoolean("DynamicBackup")) {
                                if (path.getParent().toString().endsWith("region")
                                        || path.getParent().toString().endsWith("entities")
                                        || path.getParent().toString().endsWith("poi")) {
                                    boolean found = false;
                                    if (Configuration.backupInfo
                                            .contains("Data." + path.getParent().getParent().toString() + ".Chunk."
                                                    + path.getFileName().toString())) {
                                        found = true;
                                    }

                                    if (!found)
                                        return;
                                }
                            }
                        }

                        try {
                            if (sendDebugMessage) {
                                if (ServerBackupPlugin.getInstance().getConfig().getBoolean("SendLogMessages")) {
                                    ServerBackupPlugin.getInstance().getLogger().log(Level.INFO,
                                            "Zipping '" + path + "'");

                                    if (Bukkit.getConsoleSender() != sender) {
                                        sender.sendMessage("Zipping '" + path);
                                    }
                                }
                            }

                            zs.putNextEntry(zipEntry);

                            if (System.getProperty("os.name").startsWith("Windows")
                                    && path.toString().contains("session.lock")) {
                            } else {
                                try {
                                    Files.copy(path, zs);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            zs.closeEntry();
                        } catch (IOException e) {
                            e.printStackTrace();
                            ServerBackupPlugin.getInstance().getLogger().log(Level.WARNING, ERROR_ZIPPING);
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                ServerBackupPlugin.getInstance().getLogger().log(Level.WARNING, ERROR_ZIPPING);
                return;
            }

            long time = (System.nanoTime() - sTime) / 1000000;

            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "");
            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "ServerBackup | Files zipped. [" + time + "ms]");
            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "");

            if (!isSaving) {
                File file = new File(sourceFilePath);

                try {
                    FileUtils.deleteDirectory(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            sender.sendMessage(OperationHandler.processMessage("Command.Zip.Footer").replace(FILE_NAME_PLACEHOLDER, sourceFilePath));

            OperationHandler.tasks.remove("CREATE {" + sourceFilePath.replace("\\", "/") + "}");

            if (!isFullBackup) {
                if (ServerBackupPlugin.getInstance().getConfig().getBoolean("DynamicBackup")) {
                    if (!sourceFilePath.equalsIgnoreCase(".")) {
                        Configuration.backupInfo.set("Data." + sourceFilePath, "");

                        new File(targetFilePath).renameTo(new File(targetFilePath.split("backup")[0] + "dynamic-backup" // wont comply to java:S1192, no comment
                                + targetFilePath.split("backup")[1]));
                        targetFilePath = targetFilePath.split("backup")[0] + "dynamic-backup"
                                + targetFilePath.split("backup")[1];

                        Configuration.saveBackupInfo();
                    }
                }
            }

            if (ServerBackupPlugin.getInstance().getConfig().getBoolean("Ftp.UploadBackup")) {
                FTPManager ftpm = new FTPManager(sender);
                ftpm.uploadFileToFTP(targetFilePath, false);
            }

            if (ServerBackupPlugin.getInstance().getConfig().getBoolean("CloudBackup.Dropbox")) {
                DropboxManager dm = new DropboxManager(sender);
                dm.uploadToDropbox(targetFilePath);
            }

            for (Player all : Bukkit.getOnlinePlayers()) {
                if (all.hasPermission("backup.notification")) {
                    all.sendMessage(OperationHandler.processMessage("Info.BackupFinished").replace(FILE_NAME_PLACEHOLDER, sourceFilePath));
                }
            }
        });

        if (ServerBackupPlugin.getInstance().getConfig().getString(PATH_COMMAND_AFTER_AUTOMATIC_BACKUP) != null && !isCommandTimerRunning) {
            if (!ServerBackupPlugin.getInstance().getConfig().getString(PATH_COMMAND_AFTER_AUTOMATIC_BACKUP).equalsIgnoreCase("/")) {
                isCommandTimerRunning = true;

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (OperationHandler.tasks.size() == 0) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ServerBackupPlugin.getInstance().getConfig().getString(PATH_COMMAND_AFTER_AUTOMATIC_BACKUP).replace("/", ""));

                            isCommandTimerRunning = false;

                            cancel();
                        }
                    }
                }.runTaskTimer(ServerBackupPlugin.getInstance(), 20 * 5, 20 * 5);
            }
        }
    }

    public void unzip() {
        Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getInstance(), () -> {

            long sTime = System.nanoTime();

            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "");
            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "ServerBackup | Start unzipping...");
            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "");

            byte[] buffer = new byte[1024];
            try {
                File folder = new File(targetFilePath);
                if (!folder.exists()) {
                    folder.mkdir();
                }
                ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceFilePath));
                ZipEntry ze = zis.getNextEntry();
                while (ze != null) {
                    String fileName = ze.getName();
                    File newFile = new File(targetFilePath + File.separator + fileName);

                    if (sendDebugMessage) {
                        if (ServerBackupPlugin.getInstance().getConfig().getBoolean("SendLogMessages")) {
                            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "Unzipping '" + newFile.getPath());

                            if (Bukkit.getConsoleSender() != sender) {
                                sender.sendMessage("Unzipping '" + newFile.getPath());
                            }
                        }
                    }

                    new File(newFile.getParent()).mkdirs();
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    ze = zis.getNextEntry();
                }
                zis.closeEntry();
                zis.close();
            } catch (IOException e) {
                e.printStackTrace();
                ServerBackupPlugin.getInstance().getLogger().log(Level.WARNING, "Error while unzipping files.");
                return;
            }

            long time = (System.nanoTime() - sTime) / 1000000;

            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "");
            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "ServerBackup | Files unzipped. [" + time + "ms]");
            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "");

            File file = new File(sourceFilePath);

            file.delete();

            sender.sendMessage(OperationHandler.processMessage("Command.Unzip.Footer").replace(FILE_NAME_PLACEHOLDER, sourceFilePath));
        });
    }

}
