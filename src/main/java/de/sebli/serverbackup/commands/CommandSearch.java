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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class CommandSearch {
    private CommandSearch() {
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

            List<File> backupsMatch = new ArrayList<>();

            for (File backup : backups) {
                if (backup.getName().contains(args[1])) {
                    backupsMatch.add(backup);
                }
            }

            if (backupsMatch.isEmpty()) {
                logHandler.logCommandFeedback(OperationHandler.processMessage("NoBackupSearch").replace("%input%", args[1]), sender);

                return;
            }

            Collections.sort(backupsMatch);

            try {
                int page = Integer.parseInt(args[2]);

                if (backups.length < page * 10 - 9) {
                    sender.sendMessage("Try a lower value.");

                    return;
                }

                int count = page * 10 - 9;

                if (backupsMatch.size() <= page * 10 && backupsMatch.size() >= page * 10 - 10) {
                    sender.sendMessage("----- Backup " + (page * 10 - 9) + "-"
                            + backupsMatch.size() + "/" + backupsMatch.size() + " -----");
                } else {
                    sender.sendMessage("----- Backup " + (page * 10 - 9) + "-"
                            + page * 10 + "/" + backupsMatch.size() + " -----");
                }
                sender.sendMessage("");

                for (File file : backupsMatch) {
                    if (count <= page * 10 && count <= backupsMatch.size()) {
                        double fileSize = (double) FileUtils.sizeOf(file) / 1000 / 1000;
                        fileSize = Math.round(fileSize * 100.0) / 100.0;

                        if (sender instanceof Player p) {

                            TextComponent msg = new TextComponent("§7[" + count
                                    + "] §r" + file.getName() + " §7[" + fileSize + "MB]");
                            msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    new ComponentBuilder("Click to get Backup name").create()));
                            msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                    "/backup remove " + file.getName()));

                            p.spigot().sendMessage(msg);
                        } else {
                            sender.sendMessage(file.getName());
                        }
                    }
                    count++;
                }

                int maxPages = backupsMatch.size() / 10;

                if (backupsMatch.size() % 10 != 0) {
                    maxPages++;
                }

                sender.sendMessage("");
                sender.sendMessage("-------- Page " + page + "/" + maxPages + " --------");
            } catch (Exception e) {
                logHandler.logError(OperationHandler.processMessage("Error.NotANumber").replace("%input%", args[2]), e.getMessage(), sender);
            }
        });
    }

}
