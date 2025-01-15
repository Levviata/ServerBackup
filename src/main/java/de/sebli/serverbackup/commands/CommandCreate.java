package de.sebli.serverbackup.commands;

import com.google.common.io.Files;
import de.sebli.serverbackup.Configuration;
import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.Backup;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.utils.enums.TaskPurpose;
import de.sebli.serverbackup.utils.enums.TaskType;
import de.sebli.serverbackup.utils.records.Task;
import org.apache.commons.io.FilenameUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;

import static de.sebli.serverbackup.ServerBackupPlugin.sendMessageWithLogs;
import static de.sebli.serverbackup.core.OperationHandler.formatPath;
import static de.sebli.serverbackup.utils.GlobalConstants.FILE_NAME_PLACEHOLDER;
import static de.sebli.serverbackup.utils.TaskUtils.addTask;
import static de.sebli.serverbackup.utils.TaskUtils.removeTask;

class CommandCreate {
    private CommandCreate() {
        throw new IllegalStateException("Utility class");
    }

    public static void execute(CommandSender sender, String[] args) {
        StringBuilder fileNameBuilder = new StringBuilder(args[1]);
        boolean fullBackup = false;

        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("-full")) {
                    fullBackup = true;
                } else {
                    fileNameBuilder.append(" ").append(args[i]);
                }
            }
        }
        String fileName = fileNameBuilder.toString();

        File file = new File(fileName);

        if (!file.isDirectory() && !args[1].equalsIgnoreCase("@server")) {
            Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getPluginInstance(), () -> {
                Task currentTask = addTask(TaskType.PHYSICAL, TaskPurpose.CREATE, "Creating backup via command " + formatPath(file.getPath()));

                try {
                    File destination = new File(Configuration.backupDestination + "//Files//"
                            + file.getName().replace("/", "-"));

                    if (destination.exists()) {
                        destination = new File(destination.getPath()
                                .replaceAll("." + FilenameUtils.getExtension(destination.getName()), "") + " "
                                + (System.currentTimeMillis() / 1000) + "."
                                + FilenameUtils.getExtension(file.getName()));
                    }

                    Files.copy(file, destination);

                    sendMessageWithLogs(OperationHandler.processMessage("Info.BackupFinished").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
                } catch (IOException e) {
                    sendMessageWithLogs(OperationHandler.processMessage("Error.BackupFailed").replace(FILE_NAME_PLACEHOLDER, args[1]), sender);
                    e.printStackTrace();
                } finally {
                    removeTask(currentTask);
                }
            });
        } else {
            Backup backup = new Backup(fileName, sender, fullBackup);

            backup.create();
        }
    }
}
