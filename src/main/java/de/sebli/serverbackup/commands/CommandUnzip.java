package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.core.ZipManager;
import org.bukkit.command.CommandSender;

import java.io.File;

import static de.sebli.serverbackup.ServerBackupPlugin.sendMessageWithLogs;
import static de.sebli.serverbackup.utils.GlobalConstants.FILE_NAME_PLACEHOLDER;

class CommandUnzip {
    private CommandUnzip() {
        throw new IllegalStateException("Utility class");
    }

    public static void execute(CommandSender sender, String[] args) {
        String filePath = args[1];

        if (!args[1].contains(".zip")) {
            sendMessageWithLogs(OperationHandler.processMessage("Error.NotAZip").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);

            return;
        }

        File file = new File(Configuration.backupDestination + "//" + filePath);
        File newFile = new File(
                Configuration
                        .backupDestination + "//" + filePath.replaceAll(".zip", ""));

        if (!newFile.exists()) {
            sendMessageWithLogs(OperationHandler.processMessage("Command.Unzip.Header"), sender);

            if (file.exists()) {
                ZipManager zm = new ZipManager(file.getPath(),
                        Configuration.backupDestination + "//" + newFile.getName(), sender,
                        false, true, true);

                zm.unzip();
            } else {
                sendMessageWithLogs(OperationHandler.processMessage("Error.NoBackupFound").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
            }
        } else {
            sendMessageWithLogs(OperationHandler.processMessage("Error.ZipExists").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
        }
    }

}
