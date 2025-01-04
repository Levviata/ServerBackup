package de.sebli.serverbackup;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;

import static de.sebli.serverbackup.utils.GlobalConstants.FILE_NAME_PLACEHOLDER;

class Messages {
    private Messages() {
        throw new IllegalStateException("Utility class");
    }

    private static YamlConfiguration messages = Configuration.messages;

    public static void loadMessages() {
        messages.options().header("Use '&nl' to add a new line. Use '&' for color codes (e.g. '&4' for color red). For some messages you can use a placeholder (e.g. '" + FILE_NAME_PLACEHOLDER + "' for file name)." +
                "\nMinecraft color codes: https://htmlcolorcodes.com/minecraft-color-codes/");
        messages.options().copyDefaults(true);

        messages.addDefault("Prefix", ""); // wont comply to java:S1192, maybe we never refactor this? idk

        messages.addDefault("Command.Zip.Header", "Zipping Backup..."
                + "&nl");
        messages.addDefault("Command.Zip.Footer", "&nl"
                + "&nlBackup [" + FILE_NAME_PLACEHOLDER + "] zipped."
                + "&nlBackup [" + FILE_NAME_PLACEHOLDER + "] saved.");
        messages.addDefault("Command.Unzip.Header", "Unzipping Backup..."
                + "&nl");
        messages.addDefault("Command.Unzip.Footer", "&nl"
                + "&nlBackup [" + FILE_NAME_PLACEHOLDER + "] unzipped.");
        messages.addDefault("Command.Reload", "Config reloaded.");
        messages.addDefault("Command.Tasks.Header", "----- Backup tasks -----"
                + "&nl");
        messages.addDefault("Command.Tasks.Footer", "&nl"
                + "----- Backup tasks -----");
        messages.addDefault("Command.Shutdown.Start", "The server will shut down after backup tasks (check with: '/backup tasks') are finished."
                + "&nlYou can cancel the shutdown by running this command again.");
        messages.addDefault("Command.Shutdown.Cancel", "Shutdown canceled.");

//        messages.addDefault("Info.BackupFinished", "Backup [" + FILE_NAME_PLACEHOLDER + "] saved."); // wont comply to java:S1192, we are never refactoring this
        messages.addDefault("Info.BackupStarted", "Backup [" + FILE_NAME_PLACEHOLDER + "] started."); // Same as line 38
        messages.addDefault("Info.BackupRemoved", "Backup [" + FILE_NAME_PLACEHOLDER + "] removed."); // Same as line 38
        messages.addDefault("Info.FtpUpload", "Ftp: Uploading backup [" + FILE_NAME_PLACEHOLDER + "] ...");
        messages.addDefault("Info.FtpUploadSuccess", "Ftp: Upload successfully. Backup stored on ftp server.");
        messages.addDefault("Info.FtpDownload", "Ftp: Downloading backup [" + FILE_NAME_PLACEHOLDER + "] ...");
        messages.addDefault("Info.FtpDownloadSuccess", "Ftp: Download successful. Backup downloaded from ftp server.");

        messages.addDefault("Error.NoPermission", "&cI'm sorry but you do not have permission to perform this command.");
        messages.addDefault("Error.NoBackups", "No backups found.");
        messages.addDefault("Error.NoBackupFound", "No Backup named '" + FILE_NAME_PLACEHOLDER + "' found.");
        messages.addDefault("Error.NoBackupSearch", "No backups for search argument '%input%' found.");
        messages.addDefault("Error.DeletionFailed", "Error while deleting '" + FILE_NAME_PLACEHOLDER + "'.");
        messages.addDefault("Error.FolderExists", "There is already a folder named '" + FILE_NAME_PLACEHOLDER + "'.");
        messages.addDefault("Error.ZipExists", "There is already a ZIP file named '" + FILE_NAME_PLACEHOLDER + ".zip'.");
        messages.addDefault("Error.NoFtpBackups", "No ftp backups found.");
        messages.addDefault("Error.NoTasks", "No backup tasks are running.");
        messages.addDefault("Error.AlreadyZip", FILE_NAME_PLACEHOLDER + " is already a ZIP file.");
        messages.addDefault("Error.NotAZip", FILE_NAME_PLACEHOLDER + " is not a ZIP file.");
        messages.addDefault("Error.NotANumber", "%input% is not a valid number.");
        messages.addDefault("Error.BackupFailed", "An error occurred while saving Backup [" + FILE_NAME_PLACEHOLDER + "]. See console for more information.");
        messages.addDefault("Error.FtpUploadFailed", "Ftp: Error while uploading backup to ftp server. Check server details in config.yml (ip, port, user, password).");
        messages.addDefault("Error.FtpDownloadFailed", "Ftp: Error while downloading backup to ftp server. Check server details in config.yml (ip, port, user, password).");
        messages.addDefault("Error.FtpLocalDeletionFailed", "Ftp: Local backup deletion failed because the uploaded file was not found on the ftp server. Try again.");
        messages.addDefault("Error.FtpNotFound", "Ftp: ftp-backup " + FILE_NAME_PLACEHOLDER + " not found.");
        messages.addDefault("Error.FtpConnectionFailed", "Ftp: Error while connecting to FTP server.");

        Configuration.saveMessages();

        Configuration.prefix = ((messages.getString("Prefix").equals("")) ? messages.getString("Prefix") : (messages.getString("Prefix") + " "));
    }
}
