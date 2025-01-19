package de.sebli.serverbackup;

import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.utils.LogUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static de.sebli.serverbackup.utils.GlobalConstants.CONFIG_BACKUP_DESTINATION;

public class Configuration {
    public static String prefix;
    public static String backupDestination = "Backups//";

    private static File bpInf = new File("plugins//" + ServerBackupPlugin.getPluginInstance().getName() + "//backupInfo.yml"); // wont comply to java:S1192, possibly never refactoring this?
    public static YamlConfiguration backupInfo = YamlConfiguration.loadConfiguration(bpInf);

    private static File cloudInf = new File("plugins//" + ServerBackupPlugin.getPluginInstance().getName() + "//cloudAccess.yml");
    public static YamlConfiguration cloudInfo = YamlConfiguration.loadConfiguration(cloudInf);

    private static File messagesFile = new File("plugins//" + ServerBackupPlugin.getPluginInstance().getName() + "//messages.yml");
    public static YamlConfiguration messages = YamlConfiguration.loadConfiguration(messagesFile);

    private static final String CLOUD_KEY = "Cloud.Dropbox.AppKey";

    private static final LogUtils logHandler = new LogUtils(ServerBackupPlugin.getPluginInstance());

    public static void loadUp() {
        loadConfig();
        loadCloud();
        loadBackupInfo();
        loadMessages();
    }

    public static void loadConfig() {
        if (ServerBackupPlugin.getPluginInstance().getConfig().contains(CONFIG_BACKUP_DESTINATION)) {
            backupDestination = ServerBackupPlugin.getPluginInstance().getConfig().getString(CONFIG_BACKUP_DESTINATION);
        }

        if (!Files.exists(Paths.get(backupDestination))) {
            try {
                Files.createDirectories(Paths.get(backupDestination));
            } catch (IOException e) {
                logHandler.logError("Caught an exception trying to create a path to [" + backupDestination + "]", e.getMessage(), null);
            }
        }

        File files = new File(backupDestination + "//Files");

        if (!files.exists()) {
            try {
                files.mkdir();
            } catch (Exception e) {
                logHandler.logError("Caught an exception trying to create a path to [" + files.getPath() + "]", e.getMessage(), null);
            }
        }

        StringBuilder headerText = new StringBuilder();
        headerText.append("BackupTimer = At what time should a Backup be created? The format is: 'hh-mm' e.g. '12-30'.")
                .append("\nDeleteOldBackups = Deletes old backups automatically after a specific time (in days, standard = 7 days)")
                .append("\nDeleteOldBackups - Type '0' at DeleteOldBackups to disable the deletion of old backups.")
                .append("\nBackupLimiter = Deletes old backups automatically if number of total backups is greater than this number (e.g. if you enter '5' - the oldest backup will be deleted if there are more than 5 backups, so you will always keep the latest 5 backups)")
                .append("\nBackupLimiter - Type '0' to disable this feature. If you don't type '0' the feature 'DeleteOldBackups' will be disabled and this feature ('BackupLimiter') will be enabled.")
                .append("\nKeepUniqueBackups - Type 'true' to disable the deletion of unique backups. The plugin will keep the newest backup of all backed up worlds or folders, no matter how old it is.")
                .append("\nBlacklist - A list of files/directories that will not be backed up.")
                .append("\nIMPORTANT FTP information: Set 'UploadBackup' to 'true' if you want to store your backups on a ftp server (sftp does not work at the moment - if you host your own server (e.g. vps/root server) you need to set up a ftp server on it).")
                .append("\nIf you use ftp backups, you can set 'DeleteLocalBackup' to 'true' if you want the plugin to remove the created backup from your server once it has been uploaded to your ftp server.")
                .append("\nCompressBeforeUpload compresses the backup to a zip file before uploading it. Set it to 'false' if you want the files to be uploaded directly to your ftp server.")
                .append("\nJoin the discord server if you need help or have a question: https://discord.gg/rNzngsCWFC");

        ServerBackupPlugin.getPluginInstance().getConfig().options().header(headerText.toString());

        ServerBackupPlugin.getPluginInstance().getConfig().options().copyDefaults(true);

        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("AutomaticBackups", true);
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("CommandAfterAutomaticBackup", "/");

        List<String> days = new ArrayList<>();
        days.add("MONDAY");
        days.add("TUESDAY");
        days.add("WEDNESDAY");
        days.add("THURSDAY");
        days.add("FRIDAY");
        days.add("SATURDAY");
        days.add("SUNDAY");

        List<String> times = new ArrayList<>();
        times.add("00-00");

        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("BackupTimer.Days", days);
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("BackupTimer.Times", times);

        List<String> worlds = new ArrayList<>();
        worlds.add("world");
        worlds.add("world_nether");
        worlds.add("world_the_end");

        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("BackupWorlds", worlds);

        List<String> blacklist = new ArrayList<>();
        blacklist.add("libraries");
        blacklist.add("plugins/ServerBackup/config.yml");

        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("Blacklist", blacklist);

        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("DeleteOldBackups", 14);
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("BackupLimiter", 0);

        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("KeepUniqueBackups", false);

        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("UpdateAvailableMessage", true);
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("AutomaticUpdates", true);

        ServerBackupPlugin.getPluginInstance().getConfig().addDefault(CONFIG_BACKUP_DESTINATION, "Backups//");

        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("CloudBackup.Dropbox", false);
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("CloudBackup.Options.Destination", "/");
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("CloudBackup.Options.DeleteLocalBackup", false);

        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("Ftp.UploadBackup", false);
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("Ftp.DeleteLocalBackup", false);
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("Ftp.Server.IP", "127.0.0.1");
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("Ftp.Server.Port", 21);
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("Ftp.Server.User", "username");
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("Ftp.Server.Password", "password");
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("Ftp.Server.BackupDirectory", "Backups/");

        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("DynamicBackup", false);
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("SendLogMessages", false);
        ServerBackupPlugin.getPluginInstance().getConfig().addDefault("SendDebuggingLogs", false);

        ServerBackupPlugin.getPluginInstance().saveConfig();

        backupDestination = ServerBackupPlugin.getPluginInstance().getConfig().getString(CONFIG_BACKUP_DESTINATION);
    }

    public static void loadCloud() {
        boolean wasFileCreated = false;

        if (!cloudInf.exists()) {
            try {
                wasFileCreated = cloudInf.createNewFile();
            } catch (IOException e) {
                logHandler.logError("Caught an exception trying to load cloud info", e.getMessage(), null);
            } finally {
                if (wasFileCreated)
                    logHandler.logInfo("Cloud info was loaded successfully", null);
                else
                    logHandler.logError("Cloud info was not loaded", "", null);
            }
        }

        cloudInfo.options().header("Dropbox - Watch this video for explanation: https://youtu.be/k-0aIohxRUA");

        if (!cloudInfo.contains("Cloud.Dropbox")) {
            cloudInfo.set(CLOUD_KEY, "appKey");
            cloudInfo.set("Cloud.Dropbox.AppSecret", "appSecret");
        } else {
            if (!"appKey".equals(cloudInfo.getString(CLOUD_KEY)) && !cloudInfo.contains("Cloud.Dropbox.ActivationLink")) {
                cloudInfo.set("Cloud.Dropbox.ActivationLink", "https://www.dropbox.com/oauth2/authorize?client_id=" + cloudInfo.getString(CLOUD_KEY) + "&response_type=code&token_access_type=offline");
                if (!cloudInfo.contains("Cloud.Dropbox.AccessToken")) {
                    cloudInfo.set("Cloud.Dropbox.AccessToken", "accessToken");
                }
            }
        }

        saveCloud();
    }

    public static void saveCloud() {
        try {
            cloudInfo.save(cloudInf);
        } catch (IOException e) {
            logHandler.logError("Caught an exception trying to save cloud info", e.getMessage(), null);
        }
    }

    public static void loadBackupInfo() {
        boolean wasFileCreated = false;

        if (!bpInf.exists()) {
            try {
                wasFileCreated = bpInf.createNewFile();
            } catch (IOException e) {
                logHandler.logError("Caught an exception trying to load backup info", e.getMessage(), null);
            } finally {
                if (wasFileCreated)
                    logHandler.logInfo("Backup info was successfully loaded", null);
                else
                    logHandler.logError("Backup info was not loaded", "", null);
            }
        }

        saveBackupInfo();
    }

    public static void loadMessages() {
        boolean wasFileCreated = false;

        if (!messagesFile.exists()) {
            try {
                wasFileCreated = messagesFile.createNewFile();
            } catch (IOException e) {
                logHandler.logError("Caught an exception trying to create messages file", e.getMessage(), null);
            } finally {
                if (wasFileCreated)
                    logHandler.logInfo("Messages file was successfully created", null);
                else
                    logHandler.logError("Messages file was not created", "", null);
            }
        }

        Messages.loadMessages();
    }

    public static void saveBackupInfo() {
        try {
            backupInfo.save(bpInf);
        } catch (IOException e) {
            logHandler.logError("Caught an exception trying to save backup info", e.getMessage(), null);
        }
    }

    public static void saveMessages() {
        try {
            messages.save(messagesFile);
        } catch (IOException e) {
            logHandler.logError("Caught an exception trying to save messages file", e.getMessage(), null);
        }
    }

    public static void reloadConfig(CommandSender sender) {
        ServerBackupPlugin.getPluginInstance().reloadConfig();

        OperationHandler.stopTimer();
        OperationHandler.startTimer();

        String oldDes = backupDestination;

        if (!oldDes.equalsIgnoreCase(
                ServerBackupPlugin.getPluginInstance().getConfig().getString(CONFIG_BACKUP_DESTINATION))
        ) {
            backupDestination = ServerBackupPlugin.getPluginInstance().getConfig()
                    .getString(CONFIG_BACKUP_DESTINATION);

            String formattedMessage = String.format("ServerBackup: Backup destination [ [%s] >> [%s] ] updated successfully.", oldDes, backupDestination);

            ServerBackupPlugin.getPluginInstance().getLogger().info(formattedMessage);
        }

        if (cloudInf.exists()) {
            saveCloud();
        }

        loadUp();

        logHandler.logInfo(OperationHandler.processMessage("Command.Reload"), sender);
    }

}
