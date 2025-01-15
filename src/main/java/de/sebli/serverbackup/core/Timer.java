package de.sebli.serverbackup.core;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;

import static de.sebli.serverbackup.utils.FileUtil.tryDeleteFile;
import static de.sebli.serverbackup.utils.TaskUtils.getTasks;

public class Timer implements Runnable {

    private static final List<String> worlds = ServerBackupPlugin.getPluginInstance().getConfig().getStringList("BackupWorlds");
    private static final List<String> days = ServerBackupPlugin.getPluginInstance().getConfig().getStringList("BackupTimer.Days");
    private static final List<String> times = ServerBackupPlugin.getPluginInstance().getConfig().getStringList("BackupTimer.Times");

    private Calendar cal = Calendar.getInstance();
    private short checkMinute = 0;

    @Override
    public void run() {
        cal = Calendar.getInstance();

        if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("AutomaticBackups")) {
            short timeCode = (short) (cal.get(Calendar.HOUR_OF_DAY) * 100 + cal.get(Calendar.MINUTE));

            if (checkMinute != timeCode) {
                checkMinute = timeCode;
                handleBackupOnScheduledDay();
            }
        }

        if (ServerBackupPlugin.getPluginInstance().getConfig().getInt("BackupLimiter") <= 0) {
            handleOldBackupDeletion();
        } else {
            handleBackupLimiter();
        }

        if (OperationHandler.getShutdownProgress() && getTasks().isEmpty()) {
            ServerBackupPlugin.getPluginInstance().getLogger().info("All tasks finished. Shutting down server...");
            Bukkit.shutdown();
        }
    }

    private void handleBackupOnScheduledDay() {
        boolean isBackupDay = days.stream()
                .anyMatch(d -> d.equalsIgnoreCase(getDayName(cal.get(Calendar.DAY_OF_WEEK))));

        if (isBackupDay) {
            for (String time : times) {
                try {
                    String[] timeStr = time.split("-");
                    cleanTimeString(timeStr);

                    byte hour = Byte.parseByte(timeStr[0]);
                    byte minute = Byte.parseByte(timeStr[1]);

                    if ((byte) cal.get(Calendar.HOUR_OF_DAY) == hour && (byte) cal.get(Calendar.MINUTE) == minute) {
                        createBackupsForWorlds();
                    }
                } catch (Exception e) {
                    ServerBackupPlugin.getPluginInstance().getLogger().log(Level.WARNING,
                            "Automatic Backup failed. Please check that you set the BackupTimer correctly.");
                }
            }
        }
    }

    private void cleanTimeString(String[] timeStr) {
        if (timeStr[0].startsWith("0")) {
            timeStr[0] = timeStr[0].substring(1);
        }

        if (timeStr[1].startsWith("0")) {
            timeStr[1] = timeStr[1].substring(1);
        }
    }

    private void createBackupsForWorlds() {
        for (String world : worlds) {
            Backup backup = new Backup(world, Bukkit.getConsoleSender(),
                    !ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("DynamicBackup"));
            backup.create();
        }
    }

    private void handleOldBackupDeletion() {
        if (cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0) {
            if (ServerBackupPlugin.getPluginInstance().getConfig().getInt("DeleteOldBackups") <= 0) {
                return;
            }

            deleteOldBackups();
        }
    }

    private void deleteOldBackups() {
        File[] backups = new File(Configuration.backupDestination).listFiles();

        if (backups.length == 0) return;

        Arrays.sort(backups, Collections.reverseOrder());

        LocalDate date = LocalDate.now()
                .minusDays(ServerBackupPlugin.getPluginInstance().getConfig().getInt("DeleteOldBackups"));

        logBackupDeletionStart();

        long time = System.currentTimeMillis();
        List<String> backupNames = new ArrayList<>();

        for (File backup : backups) {
            if (!backup.isFile()) {
                continue; // Skip directories
            }

            try {
                String[] backupDateStr = backup.getName().split("-");
                LocalDate backupDate = LocalDate.parse(backupDateStr[1] + "-" + backupDateStr[2] + "-" + backupDateStr[3].split("~")[0]);
                String backupName = backupDateStr[6];

                if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("KeepUniqueBackups") &&
                        !backupNames.contains(backupName)) {

                        backupNames.add(backupName);
                        continue;
                    }


                if (backupDate.isBefore(date.plusDays(1))) {
                    if (backup.exists()) {
                        tryDeleteFile(backup);

                        String formattedMessage = String.format("Backup [ [%s] ] removed.", backup.getName());
                        ServerBackupPlugin.getPluginInstance().getLogger().info(formattedMessage);
                    } else {
                        String formattedMessage = String.format("No Backup named [ [%s] ] found.", backup.getName());
                        ServerBackupPlugin.getPluginInstance().getLogger().warning(formattedMessage);
                    }
                }
            } catch (Exception e) {
                String formattedMessage = String.format("Failed to process backup file: [%s]. Reason: %s", backup.getName(), e.getMessage());
                ServerBackupPlugin.getPluginInstance().getLogger().warning(formattedMessage);
            }
        }

        logBackupDeletionEnd(time);
    }

    private void logBackupDeletionStart() {
        // Get the current time in a readable format
        String startTime = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalDateTime.now());

        // Format the message with the start time
        String formattedMessage = String.format("ServerBackup | Backup deletion started... [ started at [%s] ]", startTime);

        // Log the message
        ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, "");
        ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, formattedMessage);
        ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, "");
    }

    private void logBackupDeletionEnd(long time) {
        String formattedMessage = String.format("ServerBackup | Backup deletion finished. [ finished in [%s]ms ]", (System.currentTimeMillis() - time));

        ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, "");
        ServerBackupPlugin.getPluginInstance().getLogger().info(formattedMessage);
        ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, "");
    }

    private void handleBackupLimiter() {
        File[] backups = new File(Configuration.backupDestination).listFiles();
        Arrays.sort(Objects.requireNonNull(backups));

        int dobc = ServerBackupPlugin.getPluginInstance().getConfig().getInt("BackupLimiter");
        int c = 0;

        while (backups.length > dobc) {
            if (backups[c].exists()) {
                tryDeleteFile(backups[c]);

                String formattedMessage = String.format("Backup [%s] removed.", backups[c].getName());

                ServerBackupPlugin.getPluginInstance().getLogger().info(formattedMessage);
            } else {
                String formattedMessage = String.format("No Backup named [%s] found.", backups[c].getName());

                ServerBackupPlugin.getPluginInstance().getLogger().warning(formattedMessage);
            }

            c++;
            dobc++;
        }
    }

    private String getDayName(int dayNumber) {
        String[] daysOfWeek = {"SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};
        if (dayNumber >= 1 && dayNumber <= 7) {
            return daysOfWeek[dayNumber - 1];
        }
        Bukkit.getLogger().log(Level.WARNING, "Error while converting number in day.");
        return null;
    }
}
