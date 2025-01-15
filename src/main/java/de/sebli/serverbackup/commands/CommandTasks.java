package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.core.OperationHandler;
import org.bukkit.command.CommandSender;

import static de.sebli.serverbackup.ServerBackupPlugin.sendMessageWithLogs;
import static de.sebli.serverbackup.utils.TaskUtils.getFormattedTasks;

class CommandTasks {
    private CommandTasks() {
        throw new IllegalStateException("Utility class");
    }

    public static void execute(CommandSender sender, String[] args) {
        if (!getFormattedTasks().isEmpty()) {
            sendMessageWithLogs(OperationHandler.processMessage("Command.Tasks.Header"), sender);

            for (String formattedTask : getFormattedTasks()) {
                sendMessageWithLogs(formattedTask, sender);
            }

            sendMessageWithLogs(OperationHandler.processMessage("Command.Tasks.Footer"), sender);
        } else {
            sendMessageWithLogs(OperationHandler.processMessage("Error.NoTasks"), sender);
        }
    }
}
