package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.core.ZipManager;
import de.sebli.serverbackup.utils.enums.TaskPurpose;
import de.sebli.serverbackup.utils.enums.TaskType;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static de.sebli.serverbackup.ServerBackupPlugin.sendMessageWithLogs;
import static de.sebli.serverbackup.core.OperationHandler.formatPath;
import static de.sebli.serverbackup.utils.GlobalConstants.FILE_NAME_PLACEHOLDER;
import static de.sebli.serverbackup.utils.TaskUtils.addTask;

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

        File file = Paths.get(Configuration.backupDestination, filePath).toFile();
        File newFile = Paths.get(Configuration.backupDestination, filePath + ".zip").toFile();

        if (!newFile.exists()) {
            sender.sendMessage(OperationHandler.processMessage("Command.Zip.Header"));

            if (file.exists()) {
                ZipManager zm = new ZipManager(file.getPath(), newFile.getPath(), sender, true, false,
                        true);

                zm.zip(addTask(TaskType.PHYSICAL, TaskPurpose.ZIP, "Zipping via command " + formatPath(filePath)));
            } else {
                sendMessageWithLogs(OperationHandler.processMessage("Error.NoBackupFound").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
            }
        } else {
            sendMessageWithLogs(OperationHandler.processMessage("Error.FolderExists").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
        }
    }

}
