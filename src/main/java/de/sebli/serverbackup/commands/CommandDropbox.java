package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.utils.DropboxManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

class CommandDropbox {
    private CommandDropbox() {
        throw new IllegalStateException("Utility class");
    }

    public static void execute(CommandSender sender, String[] args) {
        if (args[1].equalsIgnoreCase("upload")) {
            Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getPluginInstance(), () -> {
                DropboxManager dm = new DropboxManager(sender);

                dm.uploadToDropbox(args[2]);
            });
        }
    }
}
