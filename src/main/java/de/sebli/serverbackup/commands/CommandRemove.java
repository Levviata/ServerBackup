package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.core.Backup;
import org.bukkit.command.CommandSender;

class CommandRemove {
    private CommandRemove() {
        throw new IllegalStateException("Utility class");
    }

    public static void execute(CommandSender sender, String[] args) {
        if (args[1].equalsIgnoreCase("Files")) {
            sender.sendMessage("You can not delete the 'Files' backup folder.");

            return;
        }

        Backup backup = new Backup(args[1], sender, true);

        backup.remove();
    }

}
