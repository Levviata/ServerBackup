package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.Backup;
import de.sebli.serverbackup.utils.LogUtils;
import org.bukkit.command.CommandSender;

class CommandRemove {
    private CommandRemove() {
        throw new IllegalStateException("Utility class");
    }

    private static final LogUtils logHandler = new LogUtils(ServerBackupPlugin.getPluginInstance());

    public static void execute(CommandSender sender, String[] args) {
        if (args[1].equalsIgnoreCase("Files")) {
            logHandler.logCommandFeedback("You can not delete the 'Files' backup folder.", sender);

            return;
        }

        Backup backup = new Backup(args[1], sender, true);

        backup.remove();
    }

}
