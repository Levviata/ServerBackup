package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.core.OperationHandler;
import org.bukkit.command.CommandSender;

public class CommandShutdown {

    public static void execute(CommandSender sender, String[] args) {
        if (OperationHandler.shutdownProgress) {
            OperationHandler.shutdownProgress = false;

            sender.sendMessage(OperationHandler.processMessage("Command.Shutdown.Cancel"));
        } else {
            OperationHandler.shutdownProgress = true;

            sender.sendMessage(OperationHandler.processMessage("Command.Shutdown.Start"));
        }
    }

}
