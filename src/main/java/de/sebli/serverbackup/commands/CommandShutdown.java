package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.core.OperationHandler;
import org.bukkit.command.CommandSender;

import static de.sebli.serverbackup.core.OperationHandler.getShutdownProgress;

class CommandShutdown {
    private CommandShutdown() {
        throw new IllegalStateException("Utility class");
    }
    
    public static void execute(CommandSender sender, String[] args) {
        if (getShutdownProgress()) {
            OperationHandler.setShutdownProgress(false);

            sender.sendMessage(OperationHandler.processMessage("Command.Shutdown.Cancel"));
        } else {
            OperationHandler.setShutdownProgress(true);

            sender.sendMessage(OperationHandler.processMessage("Command.Shutdown.Start"));
        }
    }

}
