package de.sebli.serverbackup.core;

import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;

import static de.sebli.serverbackup.utils.FileUtil.tryDeleteFile;

public class Timer implements Runnable {

    List<String> worlds = ServerBackupPlugin.getPluginInstance().getConfig().getStringList("BackupWorlds");
    List<String> days = ServerBackupPlugin.getPluginInstance().getConfig().getStringList("BackupTimer.Days");
    List<String> times = ServerBackupPlugin.getPluginInstance().getConfig().getStringList("BackupTimer.Times");

    Calendar cal = Calendar.getInstance();

    short checkMinute = 0;

    @Override
    public void run() {
        cal = Calendar.getInstance();

        if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("AutomaticBackups")) {
            short timeCode = (short) (cal.get(Calendar.HOUR_OF_DAY) * 100 + cal.get(Calendar.MINUTE));

            if (checkMinute != timeCode) {
                checkMinute = timeCode;

                boolean isBackupDay = days.stream().filter(d -> d.equalsIgnoreCase(getDayName(cal.get(Calendar.DAY_OF_WEEK))))
                        .findFirst().isPresent();

                if (isBackupDay) {
                    for (String time : times) {
                        try {
                            String[] timeStr = time.split("-");

                            if (timeStr[0].startsWith("0")) {
                                timeStr[0] = timeStr[0].substring(1);
                            }

                            if (timeStr[1].startsWith("0")) {
                                timeStr[1] = timeStr[1].substring(1);
                            }

                            byte hour = Byte.valueOf(timeStr[0]);
                            byte minute = Byte.valueOf(timeStr[1]);

                            if ((byte) cal.get(Calendar.HOUR_OF_DAY) == hour && (byte) cal.get(Calendar.MINUTE) == minute) {
                                for (String world : worlds) {
                                    Backup backup = new Backup(world, Bukkit.getConsoleSender(),
                                            !ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("DynamicBackup"));
                                    backup.create();
                                }
                            }
                        } catch (Exception e) {
                            ServerBackupPlugin.getPluginInstance().getLogger().log(Level.WARNING,
                                    "ServerBackup: Automatic Backup failed. Please check that you set the BackupTimer correctly.");
                        }
                    }
                }
            }
        }

        if (ServerBackupPlugin.getPluginInstance().getConfig().getInt("BackupLimiter") <= 0) {
            if (cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0) {
                if (ServerBackupPlugin.getPluginInstance().getConfig().getInt("DeleteOldBackups") <= 0)
                    return;

                File[] backups = new File(Configuration.backupDestination + "").listFiles();

                if (backups.length == 0)
                    return;

                Arrays.sort(backups, Collections.reverseOrder());

                LocalDate date = LocalDate.now()
                        .minusDays(ServerBackupPlugin.getPluginInstance().getConfig().getInt("DeleteOldBackups"));

                ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, "");
                ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, "ServerBackup | Backup deletion started...");
                ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, "");

                long time = System.currentTimeMillis();

                List<String> backupNames = new ArrayList<>();

                for (int i = 0; i < backups.length; i++) {
                    try {
                        String[] backupDateStr = backups[i].getName().split("-");
                        LocalDate backupDate = LocalDate.parse(
                                backupDateStr[1] + "-" + backupDateStr[2] + "-" + backupDateStr[3].split("~")[0]);
                        String backupName = backupDateStr[6];

                        if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("KeepUniqueBackups")) {
                            if (!backupNames.contains(backupName)) {
                                backupNames.add(backupName);
                                continue;
                            }
                        }

                        if (backupDate.isBefore(date.plusDays(1))) {
                            if (backups[i].exists()) {
                                backups[i].delete();

                                ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO,
                                        "Backup [" + backups[i].getName() + "] removed.");
                            } else {
                                ServerBackupPlugin.getPluginInstance().getLogger().log(Level.WARNING,
                                        "No Backup named '" + backups[i].getName() + "' found.");
                            }
                        }
                    } catch (Exception e) {
                    }
                }

                ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, "");
                ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, "ServerBackup | Backup deletion finished. ["
                        + Long.valueOf(System.currentTimeMillis() - time) + "ms]");
                ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO, "");
            }
        } else {
            File[] backups = new File(Configuration.backupDestination + "").listFiles();
            Arrays.sort(backups);

            int dobc = ServerBackupPlugin.getPluginInstance().getConfig().getInt("BackupLimiter");
            int c = 0;

            while (backups.length > dobc) {
                if (backups[c].exists()) {
                    tryDeleteFile(backups[c]);

                    ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO,
                            "Backup [" + backups[c].getName() + "] removed.");
                } else {
                    ServerBackupPlugin.getPluginInstance().getLogger().log(Level.WARNING,
                            "No Backup named '" + backups[c].getName() + "' found.");
                }

                c++;
                dobc++;
            }
        }

        if (OperationHandler.getShutdownProgress()) {
            if (OperationHandler.getTasks().size() == 0) {
                ServerBackupPlugin.getPluginInstance().getLogger().log(Level.INFO,
                        "Backup tasks finished. Shutting down server...");

                Bukkit.shutdown();
            }
        }
    }

    private String getDayName(int dayNumber) {
        if (dayNumber == 1) {
            return "SUNDAY";
        }

        if (dayNumber == 2) {
            return "MONDAY";
        }

        if (dayNumber == 3) {
            return "TUESDAY";
        }

        if (dayNumber == 4) {
            return "WEDNESDAY";
        }

        if (dayNumber == 5) {
            return "THURSDAY";
        }

        if (dayNumber == 6) {
            return "FRIDAY";
        }

        if (dayNumber == 7) {
            return "SATURDAY";
        }

        Bukkit.getLogger().log(Level.WARNING, "Error while converting number in day.");

        return null;
    }

}