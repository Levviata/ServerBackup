package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.core.ZipManager;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;

public class CommandZip {

    public static void execute(CommandSender sender, String[] args) {
        String filePath = args[1];

        if (args[1].contains(".zip")) {
            sender.sendMessage(OperationHandler.processMessage("Error.AlreadyZip").replace("%file%", args[1]));
            return;
        }

        File file = new File(Configuration.backupDestination + "//" + filePath);
        File newFile = new File(Configuration.backupDestination + "//" + filePath + ".zip");

        if (!newFile.exists()) {
            sender.sendMessage(OperationHandler.processMessage("Command.Zip.Header"));

            if (file.exists()) {
                try {
                    ZipManager zm = new ZipManager(file.getPath(), newFile.getPath(), sender, true, false,
                            true);

                    zm.zip();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                sender.sendMessage(OperationHandler.processMessage("Error.NoBackupFound").replace("%file%", args[1]));
            }
        } else {
            sender.sendMessage(OperationHandler.processMessage("Error.FolderExists").replace("%file%", args[1]));
        }
    }

}
