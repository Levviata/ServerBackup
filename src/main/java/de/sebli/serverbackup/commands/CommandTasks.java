package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.core.OperationHandler;
import org.bukkit.command.CommandSender;

public class CommandTasks {

    public static void execute(CommandSender sender, String[] args) {
        if (OperationHandler.tasks.size() > 0) {
            sender.sendMessage(OperationHandler.processMessage("Command.Tasks.Header"));

            for (String task : OperationHandler.tasks) {
                sender.sendMessage(task);
            }

            sender.sendMessage(OperationHandler.processMessage("Command.Tasks.Footer"));
        } else {
            sender.sendMessage(OperationHandler.processMessage("Error.NoTasks"));
        }
    }

}
