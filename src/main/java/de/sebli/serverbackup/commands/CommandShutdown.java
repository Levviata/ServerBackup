package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.core.OperationHandler;
import org.bukkit.command.CommandSender;

import static de.sebli.serverbackup.ServerBackupPlugin.sendMessageWithLogs;
import static de.sebli.serverbackup.core.OperationHandler.getShutdownProgress;

class CommandShutdown {
    private CommandShutdown() {
        throw new IllegalStateException("Utility class");
    }
    
    public static void execute(CommandSender sender, String[] args) {
        if (getShutdownProgress()) {
            OperationHandler.setShutdownProgress(false);

            sendMessageWithLogs(OperationHandler.processMessage("Command.Shutdown.Cancel"), sender);
        } else {
            OperationHandler.setShutdownProgress(true);

            sendMessageWithLogs(OperationHandler.processMessage("Command.Shutdown.Start"), sender);
        }
    }

}
