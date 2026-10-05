package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.utils.LogUtils;
import org.bukkit.command.CommandSender;

import static de.sebli.serverbackup.utils.TaskUtils.getFormattedTasks;

class CommandTasks {
    private CommandTasks() {
        throw new IllegalStateException("Utility class");
    }

    private static final LogUtils logHandler = new LogUtils(ServerBackupPlugin.getPluginInstance());

    public static void execute(CommandSender sender, String[] args) {
        if (!getFormattedTasks().isEmpty()) {
            logHandler.logInfo(OperationHandler.processMessage("Command.Tasks.Header"), sender);

            for (String formattedTask : getFormattedTasks()) {
                logHandler.logInfo(formattedTask, sender);
            }

            logHandler.logInfo(OperationHandler.processMessage("Command.Tasks.Footer"), sender);
        } else {
            logHandler.logCommandFeedback(OperationHandler.processMessage("Error.NoTasks"), sender);
        }
    }
}
