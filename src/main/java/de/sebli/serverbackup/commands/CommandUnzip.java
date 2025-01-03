package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackup;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.core.ZipManager;
import org.bukkit.command.CommandSender;

import java.io.File;

public class CommandUnzip {

    public static void execute(CommandSender sender, String[] args) {
        String filePath = args[1];

        if (!args[1].contains(".zip")) {
            sender.sendMessage(OperationHandler.processMessage("Error.NotAZip").replaceAll("%file%", args[1]));

            return;
        }

        File file = new File(Configuration.backupDestination + "//" + filePath);
        File newFile = new File(
                Configuration
                        .backupDestination + "//" + filePath.replaceAll(".zip", ""));

        if (!newFile.exists()) {
            sender.sendMessage(OperationHandler.processMessage("Command.Unzip.Header"));

            if (file.exists()) {
                ZipManager zm = new ZipManager(file.getPath(),
                        Configuration.backupDestination + "//" + newFile.getName(), sender,
                        false, true, true);

                zm.unzip();
            } else {
                sender.sendMessage(OperationHandler.processMessage("Error.NoBackupFound").replaceAll("%file%", args[1]));
            }
        } else {
            sender.sendMessage(OperationHandler.processMessage("Error.ZipExists").replaceAll("%file%", args[1]));
        }
    }

}
