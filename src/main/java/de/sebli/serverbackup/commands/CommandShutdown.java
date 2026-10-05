package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.utils.LogUtils;
import org.bukkit.command.CommandSender;

import static de.sebli.serverbackup.core.OperationHandler.getShutdownProgress;

class CommandShutdown {
    private CommandShutdown() {
        throw new IllegalStateException("Utility class");
    }

    private static final LogUtils logHandler = new LogUtils(ServerBackupPlugin.getPluginInstance());

    public static void execute(CommandSender sender, String[] args) {
        if (getShutdownProgress()) {
            OperationHandler.setShutdownProgress(false);

            logHandler.logInfo(OperationHandler.processMessage("Command.Shutdown.Cancel"), sender);
        } else {
            OperationHandler.setShutdownProgress(true);

            logHandler.logInfo(OperationHandler.processMessage("Command.Shutdown.Start"), sender);
        }
    }

}
