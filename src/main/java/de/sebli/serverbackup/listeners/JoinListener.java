package de.sebli.serverbackup.listeners;

import de.sebli.serverbackup.ServerBackup;
import de.sebli.serverbackup.core.OperationHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Scanner;

public class JoinListener implements Listener {

    static final String TITLE = "§8=====§fServerBackup§8=====";
    static final String AUTHOR = "§8=====§9Plugin by Seblii§8=====";
    static final String PAGE = "§7, you are on - §c";

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (p.hasPermission("backup.update")) {
            if (ServerBackup.getInstance().getConfig().getBoolean("UpdateAvailableMessage")) {
                Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), () -> {
                    int resourceID = 79320;
                    try (InputStream inputStream = (new URL(
                            "https://api.spigotmc.org/legacy/update.php?resource=" + resourceID)).openStream();
                         Scanner scanner = new Scanner(inputStream)) {
                        if (scanner.hasNext()) {
                            String latest = scanner.next();
                            String current = ServerBackup.getInstance().getDescription().getVersion();

                            int late = Integer.parseInt(latest.replaceAll("\\.", ""));  // Somewhat reasonable replaceAll() usage
                            int curr = Integer.parseInt(current.replaceAll("\\.", "")); // This too

                            if (curr >= late) {
                            } else {
                                if (OperationHandler.isUpdated) {
                                    p.sendMessage(TITLE);
                                    p.sendMessage("");
                                    p.sendMessage("§7There was a newer version available - §a" + latest
                                            + PAGE + current);
                                    p.sendMessage(
                                            "\n§7The latest version has been downloaded automatically, please reload the server to complete the update.");
                                    p.sendMessage("");
                                    p.sendMessage(AUTHOR);
                                } else {
                                    if (ServerBackup.getInstance().getConfig().getBoolean("AutomaticUpdates")) {
                                        if (p.hasPermission("backup.admin")) {
                                            p.sendMessage(TITLE);
                                            p.sendMessage("");
                                            p.sendMessage("§7There is a newer version available - §a" + latest
                                                    + PAGE + current);
                                            p.sendMessage("");
                                            p.sendMessage(AUTHOR);
                                            p.sendMessage("");
                                            p.sendMessage("ServerBackup§7: Automatic update started...");

                                            URL url = new URL(
                                                    "https://server-backup.net/assets/downloads/ServerBackup.jar");

                                            int bVer = Integer.parseInt(
                                                    Bukkit.getVersion().split(" ")[Bukkit.getVersion().split(" ").length
                                                            - 1].replaceAll("\\)", "").replaceAll("\\.", "")); // Somewhat reasonable replaceAll() usage

                                            if (bVer < 118) {
                                                url = new URL(
                                                        "https://server-backup.net/assets/downloads/alt/ServerBackup.jar");
                                            }

                                            try (InputStream in = url.openStream();
                                                 ReadableByteChannel rbc = Channels.newChannel(in);
                                                 FileOutputStream fos = new FileOutputStream(
                                                         "plugins/ServerBackup.jar")) {
                                                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                                                p.sendMessage(
                                                        "ServerBackup§7: Download finished. Please reload the server to complete the update.");

                                                OperationHandler.isUpdated = true;
                                            }
                                        }
                                    } else {
                                        p.sendMessage(TITLE);
                                        p.sendMessage("");
                                        p.sendMessage("§7There is a newer version available - §a" + latest
                                                + PAGE + current);
                                        p.sendMessage(
                                                "§7Please download the latest version - §4https://server-backup.net/");
                                        p.sendMessage("");
                                        p.sendMessage(AUTHOR);
                                    }
                                }
                            }
                        }
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }
                });
            }
        }
    }

}
