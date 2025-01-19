package de.sebli.serverbackup.core;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.utils.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.sebli.serverbackup.utils.GlobalConstants.RESOURCE_ID;

public class OperationHandler { // Won't comply to java:S1118, we actually instantiate this class

    private static boolean shutdownProgress = false;
    private static boolean isUpdated = false;

    private static final ServerBackupPlugin instance = ServerBackupPlugin.getPluginInstance();
    private static final LogUtils logHandler = new LogUtils(instance);

    public static String processMessage(String msgCode) {
        return (Configuration.prefix + Configuration.messages.getString(msgCode)).replace("&nl", "\n").replace("&", "§");
    }

    public static void startTimer() {
        if (instance.getConfig().getBoolean("AutomaticBackups")) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(instance, new Timer(), (long) 20 * 20, (long) 20 * 20);
        }
    }

    public static void stopTimer() {
        Bukkit.getScheduler().cancelTasks(instance);
    }

    public static void checkVersion(@Nullable Player player) { // TODO: Send messages to player if this gets called by JoinListener.java
        instance.getLogger().info("ServerBackup: Searching for updates...");

        Bukkit.getScheduler().runTaskAsynchronously(instance, () -> {
            try (InputStream inputStream = (new URL(
                    "https://api.spigotmc.org/legacy/update.php?resource=" + RESOURCE_ID)).openStream();
                 Scanner scanner = new Scanner(inputStream)) {
                if (scanner.hasNext()) {
                    String latest = scanner.next();
                    String current = instance.getPluginMeta().getVersion();

                    int latestClean = 0;
                    int currentClean = 0;

                    logHandler.logInfo("Auto updating does nothing for now, we have not uploaded our fork to Spigot. " +
                            "I recommend turning automatic updates off in the config.", null);

                    /*if (extractVersion(latest) == null) {
                        ServerBackupPlugin.getInstance().getLogger().warning(
                                "Latest version number extracted is null! Auto updating likely WON'T work.");
                    } else latestClean = Integer.parseInt(Objects.requireNonNull(extractVersion(latest))); Remove commenting when we actually upload our fork to spigot*/

                    if (extractVersion(current) == null) {
                        instance.getLogger().warning(
                                "Current version number extracted is null! Auto updating likely WON'T work.");
                    } else currentClean = Integer.parseInt(Objects.requireNonNull(extractVersion(current)));

                    if (instance.getConfig().getBoolean("SendDebugMessages")) {
                        logHandler.logInfo(MessageFormat.format("Latest clean version number is: {0}", latestClean), null); // TEST CODE
                        logHandler.logInfo(MessageFormat.format("Current clean version number is: {0}", currentClean), null); // TEST CODE
                    }

                    latestClean = currentClean; // DISABLE AUTO UPDATING TODO: Remove when we actually have versions up and running in spigot

                    if (currentClean == latestClean) {
                        ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO,
                                "ServerBackup: No updates found. The server is running the latest version.");
                    } else {
                        String formattedMessage = MessageFormat.format("ServerBackup: There is a newer version available - {0}, you are on - {1}", latest, current);

                        logHandler.logInfo(formattedMessage, player);

                        if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("AutomaticUpdates")) {
                            ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, "ServerBackup: Downloading newest version...");

                            URL url = new URL("https://server-backup.net/assets/downloads/alt/ServerBackup.jar");

                            if (Bukkit.getVersion().contains("1.18") || Bukkit.getVersion().contains("1.19")) {
                                url = new URL("https://server-backup.net/assets/downloads/ServerBackup.jar");
                            }

                            try (InputStream in = url.openStream();
                                 ReadableByteChannel rbc = Channels.newChannel(in);
                                 FileOutputStream fos = new FileOutputStream("plugins/ServerBackup.jar")) {
                                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                                ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO,
                                        "ServerBackup: Download finished. Please reload the server to complete the update.");

                                isUpdated = true;
                            }
                        } else {
                            ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO,
                                    "ServerBackup: Please download the latest version - https://server-backup.net/");
                        }
                    }
                }
            } catch (IOException exception) {
                String formattedMessage = MessageFormat.format("ServerBackup: Cannot search for updates - {0}", exception.getMessage());

                ServerBackupPlugin.getPluginInstance().getLogger().warning(formattedMessage);
            }
        });
    }

    private static String extractVersion(String input) {
        Pattern pattern = Pattern.compile("v(\\d+\\.\\d+\\.\\d+)");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            String version = matcher.group(1); // Extracted version in "major.minor.patch" format.
            return version.replace(".", "");  // Remove all dots from the version.
        }
        return null; // Return null if no match is found.
    }

    public static boolean getShutdownProgress() {
        return shutdownProgress;
    }

    public static void setShutdownProgress(boolean shutdownProgressIn) {
        OperationHandler.shutdownProgress = shutdownProgressIn;
    }

    public static String formatPath(String filePath) {
        return filePath.replace("\\", "/");
    }
}
