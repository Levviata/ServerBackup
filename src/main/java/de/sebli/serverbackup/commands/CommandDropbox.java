package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.ServerBackup;
import de.sebli.serverbackup.utils.DropboxManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class CommandDropbox {

    public static void execute(CommandSender sender, String[] args) {
        if(args[1].equalsIgnoreCase("upload")) {
            Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), () -> {
                DropboxManager dm = new DropboxManager(sender);

                dm.uploadToDropbox(args[2]);
            });
        }
    }
}
