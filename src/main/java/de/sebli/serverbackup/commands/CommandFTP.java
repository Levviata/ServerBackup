package de.sebli.serverbackup.commands;

import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.utils.FTPManager;
import de.sebli.serverbackup.utils.LogUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

class CommandFTP {
    private CommandFTP() {
        throw new IllegalStateException("Utility class");
    }

    private static final ServerBackupPlugin instance = ServerBackupPlugin.getPluginInstance();

    private static final LogUtils logHandler = new LogUtils(instance);

    public static void execute(CommandSender sender, String[] args) {
        if (args[1].equalsIgnoreCase("list")) {
            Bukkit.getScheduler().runTaskAsynchronously(instance, () -> {
                FTPManager ftpm = new FTPManager(sender);

                List<String> backups = ftpm.getFTPBackupList(false);

                if (backups.isEmpty()) {
                    logHandler.logInfo(OperationHandler.processMessage("Error.NoFtpBackups"), sender);

                    return;
                }

                try {
                    int page = Integer.parseInt(args[2]);

                    if (backups.size() < page * 10 - 9) {
                        sender.sendMessage("Try a lower value.");

                        return;
                    }

                    if (backups.size() <= page * 10 && backups.size() >= page * 10 - 10) {
                        sender.sendMessage("----- Ftp-Backup " + (page * 10 - 9) + "-"
                                + backups.size() + "/" + backups.size() + " -----");
                    } else {
                        sender.sendMessage("----- Ftp-Backup " + (page * 10 - 9) + "-"
                                + page * 10 + "/" + backups.size() + " -----");
                    }
                    sender.sendMessage("");

                    for (int i = page * 10 - 10; i < backups.size() && i < page * 10; i++) {
                        if (sender instanceof Player p) {

                            TextComponent msg = new TextComponent(backups.get(i));
                            msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    new ComponentBuilder("Click to get Backup name").create()));
                            msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                    backups.get(i).split(" ")[1]));

                            p.spigot().sendMessage(msg);
                        } else {
                            sender.sendMessage(backups.get(i));
                        }
                    }

                    int maxPages = backups.size() / 10;

                    if (backups.size() % 10 != 0) {
                        maxPages++;
                    }

                    sender.sendMessage("");
                    sender.sendMessage("--------- Page " + page + "/" + maxPages + " ---------");
                } catch (Exception e) {
                    logHandler.logError(OperationHandler.processMessage("Error.NotANumber").replace("%input%", args[1]), e.getMessage(), sender);
                }
            });
        } else if (args[1].equalsIgnoreCase("download")) {
            Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getPluginInstance(), () -> {
                FTPManager ftpm = new FTPManager(sender);

                ftpm.downloadFileFromFTP(args[2]);
            });
        } else if (args[1].equalsIgnoreCase("upload")) {
            Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getPluginInstance(), () -> {
                FTPManager ftpm = new FTPManager(sender);

                ftpm.uploadFileToFTP(args[2], !ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("Ftp.CompressBeforeUpload"));
            });
        }
    }

}
