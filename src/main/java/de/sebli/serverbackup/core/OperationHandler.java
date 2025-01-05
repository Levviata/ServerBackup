package de.sebli.serverbackup.core;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import org.bukkit.Bukkit;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.sebli.serverbackup.utils.GlobalConstants.RESOURCE_ID;

public class OperationHandler { // Wont comply to java:S1118, we actually instantiate this class

    public static boolean shutdownProgress = false;
    public static boolean isUpdated = false;
    private static final List<String> IDENTIFIERS_LIST = new ArrayList<>(Arrays.asList(
            "-SNAPSHOT",
            "-reobf",
            "-RC-",
            "-release"
    ));

    public static List<String> tasks = new ArrayList<>();

    public static String processMessage(String msgCode) {
        return (Configuration.prefix + Configuration.messages.getString(msgCode)).replace("&nl", "\n").replace("&", "§");
    }

    public static void startTimer() {
        if (ServerBackupPlugin.getInstance().getConfig().getBoolean("AutomaticBackups")) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(ServerBackupPlugin.getInstance(), new Timer(), 20 * 20, 20 * 20);
        }
    }

    public static void stopTimer() {
        Bukkit.getScheduler().cancelTasks(ServerBackupPlugin.getInstance());
    }

    public static void checkVersion() {
        ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "ServerBackup: Searching for updates...");

        Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getInstance(), () -> {
            try (InputStream inputStream = (new URL(
                    "https://api.spigotmc.org/legacy/update.php?resource=" + RESOURCE_ID)).openStream();
                 Scanner scanner = new Scanner(inputStream)) {
                if (scanner.hasNext()) {
                    String latest = scanner.next();
                    // Won't comply to java:S1874: getPluginMeta.getVersion() does the same but until paper doesn't go off snapshot
                    // and marks it off as unstable I'd rather not touch it
                    String current = ServerBackupPlugin.getInstance().getDescription().getVersion();

                    int latestClean = 0;
                    int currentClean = 0;

                    ServerBackupPlugin.getInstance().getLogger().info(
                            "Auto updating does nothing for now, we have not uploaded our plugin/fork to Spigot. I recommend turning automatic updates off in the config.");

                    /*if (extractVersion(latest) == null) {
                        ServerBackupPlugin.getInstance().getLogger().warning(
                                "Latest version number extracted is null! Auto updating likely WON'T work.");
                    } else latestClean = Integer.parseInt(Objects.requireNonNull(extractVersion(latest)));*/ // Remove when we actually upload our plugin to spigot

                    if (extractVersion(current) == null) {
                        ServerBackupPlugin.getInstance().getLogger().warning(
                                "Current version number extracted is null! Auto updating likely WON'T work.");
                    } else currentClean = Integer.parseInt(Objects.requireNonNull(extractVersion(current)));

                    ServerBackupPlugin.getInstance().getLogger().info(MessageFormat.format("Latest clean version number is: {0}", latestClean)); // TEST CODE

                    ServerBackupPlugin.getInstance().getLogger().info(MessageFormat.format("Current clean version number is: {0}", currentClean)); // TEST CODE

                    latestClean = currentClean; // TODO: Remove when we actually have versions up and running in spigot

                    if (currentClean == latestClean) {
                        ServerBackupPlugin.getInstance().getLogger().log(Level.INFO,
                                "ServerBackup: No updates found. The server is running the latest version.");
                    } else {
                        ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "ServerBackup: There is a newer version available - " + latest
                                + ", you are on - " + current);

                        if (ServerBackupPlugin.getInstance().getConfig().getBoolean("AutomaticUpdates")) {
                            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO, "ServerBackup: Downloading newest version...");

                            URL url = new URL("https://server-backup.net/assets/downloads/alt/ServerBackup.jar");

                            if (Bukkit.getVersion().contains("1.18") || Bukkit.getVersion().contains("1.19")) {
                                url = new URL("https://server-backup.net/assets/downloads/ServerBackup.jar");
                            }

                            try (InputStream in = url.openStream();
                                 ReadableByteChannel rbc = Channels.newChannel(in);
                                 FileOutputStream fos = new FileOutputStream("plugins/ServerBackup.jar")) {
                                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                                ServerBackupPlugin.getInstance().getLogger().log(Level.INFO,
                                        "ServerBackup: Download finished. Please reload the server to complete the update.");

                                isUpdated = true;
                            }
                        } else {
                            ServerBackupPlugin.getInstance().getLogger().log(Level.INFO,
                                    "ServerBackup: Please download the latest version - https://server-backup.net/");
                        }
                    }
                }
            } catch (IOException exception) {
                ServerBackupPlugin.getInstance().getLogger().log(Level.WARNING,
                        "ServerBackup: Cannot search for updates - " + exception.getMessage());
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
}
