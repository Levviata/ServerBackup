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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;

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
                    String current = ServerBackupPlugin.getInstance().getDescription().getVersion();

                    // Normalize versions by removing numbers, snapshot and reobf tag (if present)

                    // Will not work as of right now because of how the original version formatting is like but that is intended
                    String latestClean = cleanVersion(latest, IDENTIFIERS_LIST);

                    String currentClean = cleanVersion(current, IDENTIFIERS_LIST);

                    if (compareVersions(currentClean, latestClean) >= 0) {
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

    /**
     * Remove numbers and build identifiers/tags
     *
     * @param version Version string to clean.
     * @param identifiers A list of build identifiers to clean.
     * @return Cleaned version string without numbers and build identifiers.
     */
    private static String cleanVersion(String version, List<String> identifiers) {
        for (String identifier : identifiers) {
            if (identifier != null && !identifier.isEmpty()) {
                version = version.replace(identifier, "");
            } else ServerBackupPlugin.getInstance().getLogger().warning(
                    "WARNING - ServerBackup: Identifier list is null or empty! Might not be able to auto update!");
        }
        return version;
    }

    /**
     * Compare two version strings (e.g., "1.0.0" vs. "1.2.3").
     *
     * @param current Current version.
     * @param latest Latest version.
     * @return A negative integer if `current` is less than `latest`, zero if equal, and positive if greater.
     */
    private static int compareVersions(String current, String latest) {
        String[] currentParts = current.split("\\.");
        String[] latestParts = latest.split("\\.");
        int length = Math.max(currentParts.length, latestParts.length);

        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
            if (currentPart != latestPart) {
                return currentPart - latestPart;
            }
        }
        return 0;
    }
}
