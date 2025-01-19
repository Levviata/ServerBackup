package de.sebli.serverbackup.core;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.utils.LogUtils;
import de.sebli.serverbackup.utils.enums.TaskPurpose;
import de.sebli.serverbackup.utils.enums.TaskType;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;

import static de.sebli.serverbackup.core.OperationHandler.formatPath;
import static de.sebli.serverbackup.utils.FileUtil.tryDeleteFile;
import static de.sebli.serverbackup.utils.GlobalConstants.FILE_NAME_PLACEHOLDER;
import static de.sebli.serverbackup.utils.TaskUtils.addTask;

public class Backup {

    private final String backupFilePath;
    private final CommandSender sender;
    private final boolean isFullBackup;
    private LogUtils logHandler = new LogUtils(ServerBackupPlugin.getPluginInstance());

    public Backup(String backupFilePath, CommandSender sender, boolean isFullBackup) {
        this.backupFilePath = backupFilePath;
        this.sender = sender;
        this.isFullBackup = isFullBackup;
    }

    public String getBackupFilePath() {
        return backupFilePath;
    }

    public void create() {
        String filePath = backupFilePath;
        File worldFolder = new File(filePath);

        if (filePath.equalsIgnoreCase("@server")) {
            filePath = new File(".").getPath();
            worldFolder = new File(filePath);
        }

        Date date = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'~'HH-mm-ss");
        df.setTimeZone(TimeZone.getDefault());

        File backupFolder = Paths.get(Configuration.backupDestination, "backup-" + df.format(date) + "-" + filePath, filePath).toFile();

        if(worldFolder.exists()) {
            if (!backupFolder.exists()) {
                for (Player all : Bukkit.getOnlinePlayers()) {
                    if (all.hasPermission("backup.notification")) {
                        all.sendMessage(OperationHandler.processMessage("Info.BackupStarted").replace(FILE_NAME_PLACEHOLDER, worldFolder.getName()));
                    }
                }


                ZipManager zm = new ZipManager(
                        worldFolder.getPath(), Configuration.backupDestination + "//backup-"
                        + df.format(date) + "-" + filePath.replace("/", "-") + ".zip",
                        Bukkit.getConsoleSender(), true, true, isFullBackup);

                zm.zip(addTask(TaskType.PHYSICAL, TaskPurpose.ZIP, "Zipping " + formatPath(filePath)));
            } else {
                Bukkit.getLogger().log(Level.WARNING, "Backup already exists.");
            }
        }
    }

    public void remove() {
        Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getPluginInstance(), () -> {
            File file = Paths.get(Configuration.backupDestination, backupFilePath).toFile();

            if (file.exists()) {
                if (file.isDirectory()) {
                    try {
                        FileUtils.deleteDirectory(file);

                        logHandler.logInfo(OperationHandler.processMessage("Info.BackupRemoved").replace(FILE_NAME_PLACEHOLDER, backupFilePath), sender);
                    } catch (IOException e) {
                        logHandler.logError(OperationHandler.processMessage("Error.DeletionFailed").replace(FILE_NAME_PLACEHOLDER, backupFilePath), e.getMessage(), sender);
                    }
                } else {
                    tryDeleteFile(file);

                    logHandler.logInfo(OperationHandler.processMessage("Info.BackupRemoved").replace(FILE_NAME_PLACEHOLDER, backupFilePath), sender);
                }
            } else {
                logHandler.logCommandFeedback(OperationHandler.processMessage("Error.NoBackupFound").replace(FILE_NAME_PLACEHOLDER, backupFilePath), sender);
            }
        });
    }
}
