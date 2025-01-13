package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.core.ZipManager;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;

import static de.sebli.serverbackup.ServerBackupPlugin.sendMessageWithLogs;
import static de.sebli.serverbackup.utils.GlobalConstants.FILE_NAME_PLACEHOLDER;

class CommandZip {
    private CommandZip() {
        throw new IllegalStateException("Utility class");
    }

    public static void execute(CommandSender sender, String[] args) {
        String filePath = args[1];

        if (args[1].contains(".zip")) {
            sendMessageWithLogs(OperationHandler.processMessage("Error.AlreadyZip").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
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
                sendMessageWithLogs(OperationHandler.processMessage("Error.NoBackupFound").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
            }
        } else {
            sendMessageWithLogs(OperationHandler.processMessage("Error.FolderExists").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
        }
    }

}
