package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.core.OperationHandler;
import org.bukkit.command.CommandSender;

import static de.sebli.serverbackup.ServerBackupPlugin.sendMessageWithLogs;
class CommandTasks {
    private CommandTasks() {
        throw new IllegalStateException("Utility class");
    }

    public static void execute(CommandSender sender, String[] args) {
        if (OperationHandler.getTasks().size() > 0) {
            sender.sendMessage(OperationHandler.processMessage("Command.Tasks.Header"));

            for (String task : OperationHandler.getTasks()) {
                sender.sendMessage(task);
            }

            sendMessageWithLogs(OperationHandler.processMessage("Command.Tasks.Footer"), sender);
        } else {
            sendMessageWithLogs(OperationHandler.processMessage("Error.NoTasks"), sender);
        }
    }
}
