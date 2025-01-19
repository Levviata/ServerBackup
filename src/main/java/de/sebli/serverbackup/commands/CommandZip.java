package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.core.ZipManager;
import de.sebli.serverbackup.utils.LogUtils;
import de.sebli.serverbackup.utils.enums.TaskPurpose;
import de.sebli.serverbackup.utils.enums.TaskType;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.nio.file.Paths;

import static de.sebli.serverbackup.core.OperationHandler.formatPath;
import static de.sebli.serverbackup.utils.GlobalConstants.FILE_NAME_PLACEHOLDER;
import static de.sebli.serverbackup.utils.TaskUtils.addTask;

class CommandZip {
    private CommandZip() {
        throw new IllegalStateException("Utility class");
    }

    private static final ServerBackupPlugin instance = ServerBackupPlugin.getPluginInstance();

    private static final LogUtils logHandler = new LogUtils(instance);

    public static void execute(CommandSender sender, String[] args) {
        String filePath = args[1];

        if (args[1].contains(".zip")) {
            logHandler.logCommandFeedback(OperationHandler.processMessage("Error.AlreadyZip").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
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
                logHandler.logCommandFeedback(OperationHandler.processMessage("Error.NoBackupFound").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
            }
        } else {
            logHandler.logCommandFeedback(OperationHandler.processMessage("Error.FolderExists").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
        }
    }

}
