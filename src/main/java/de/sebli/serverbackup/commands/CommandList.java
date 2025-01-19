package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.utils.LogUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Arrays;

class CommandList {
    private CommandList() {
        throw new IllegalStateException("Utility class");
    }

    private static final ServerBackupPlugin instance = ServerBackupPlugin.getPluginInstance();

    private static final LogUtils logHandler = new LogUtils(instance);

    public static void execute(CommandSender sender, String[] args) {
        Bukkit.getScheduler().runTaskAsynchronously(instance, () -> {
            File[] backups = new File(Configuration.backupDestination).listFiles();

            if (backups.length == 0
                    || backups.length == 1 && backups[0].getName().equalsIgnoreCase("Files")) {
                logHandler.logCommandFeedback(OperationHandler.processMessage("Error.NoBackups"), sender);

                return;
            }

            Arrays.sort(backups);

            try {
                int page = Integer.parseInt(args[1]);

                if ((backups.length - 1) < page * 10 - 9) {
                    sender.sendMessage("Try a lower value.");

                    return;
                }

                if ((backups.length - 1) <= page * 10 && (backups.length - 1) >= page * 10 - 10) {
                    sender.sendMessage("----- Backup " + (page * 10 - 9) + "-"
                            + (backups.length - 1) + "/" + (backups.length - 1) + " -----");
                } else {
                    sender.sendMessage("----- Backup " + (page * 10 - 9) + "-"
                            + page * 10 + "/" + (backups.length - 1) + " -----");
                }
                sender.sendMessage("");

                for (int i = page * 10 - 10; i < (backups.length - 1) && i < page * 10; i++) {
                    if (backups[0].getName().equalsIgnoreCase("Files")) {
                        i--;
                        continue;
                    }

                    double fileSize = (double) FileUtils.sizeOf(backups[i]) / 1000 / 1000;
                    fileSize = Math.round(fileSize * 100.0) / 100.0;

                    if (sender instanceof Player p) {

                        TextComponent msg = new TextComponent("§7[" + (i + 1) + "] §r"
                                + backups[i].getName() + " §7[" + fileSize + "MB]");
                        msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new ComponentBuilder("Click to get Backup name").create()));
                        msg.setClickEvent(
                                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, backups[i].getName()));

                        p.spigot().sendMessage(msg);
                    } else {
                        sender.sendMessage(backups[i].getName());
                    }
                }

                int maxPages = (backups.length - 1) / 10;

                if ((backups.length - 1) % 10 != 0) {
                    maxPages++;
                }

                sender.sendMessage("");
                sender.sendMessage("-------- Page " + page + "/" + maxPages + " --------");
            } catch (Exception e) {
                logHandler.logError(OperationHandler.processMessage("Error.NotANumber").replace("%input%", args[1]), e.getMessage(), sender);
            }
        });
    }
}