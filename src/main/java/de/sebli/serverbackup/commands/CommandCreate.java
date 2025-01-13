package de.sebli.serverbackup.commands;

import com.google.common.io.Files;
import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.Backup;
import de.sebli.serverbackup.core.OperationHandler;
import org.apache.commons.io.FilenameUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;

import static de.sebli.serverbackup.ServerBackupPlugin.sendMessageWithLogs;
import static de.sebli.serverbackup.utils.GlobalConstants.FILE_NAME_PLACEHOLDER;

class CommandCreate {
    private CommandCreate() {
        throw new IllegalStateException("Utility class");
    }

    public static void execute(CommandSender sender, String[] args) {
        String fileName = args[1];

        boolean fullBackup = false;

        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("-full")) {
                    fullBackup = true;
                } else {
                    fileName = fileName + " " + args[i];
                }
            }
        }

        File file = new File(fileName);

        if (!file.isDirectory() && !args[1].equalsIgnoreCase("@server")) {
            Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getPluginInstance(), new Runnable() {

                @Override
                public void run() {
                    try {
                        File des = new File(Configuration.backupDestination + "//Files//"
                                + file.getName().replace("/", "-"));

                        if (des.exists()) {
                            des = new File(des.getPath()
                                    .replaceAll("." + FilenameUtils.getExtension(des.getName()), "") + " "
                                    + String.valueOf(System.currentTimeMillis() / 1000) + "."
                                    + FilenameUtils.getExtension(file.getName()));
                        }

                        Files.copy(file, des);

                        sendMessageWithLogs(OperationHandler.processMessage("Info.BackupFinished").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
                    } catch (IOException e) {
                        sendMessageWithLogs(OperationHandler.processMessage("Error.BackupFailed").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
                        e.printStackTrace();
                    }
                }

            });
        } else {
            Backup backup = new Backup(fileName, sender, fullBackup);

            backup.create();
        }
    }

}
