package de.sebli.serverbackup.utils;

import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.utils.enums.TaskPurpose;
import de.sebli.serverbackup.utils.enums.TaskType;
import de.sebli.serverbackup.utils.records.Task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static de.sebli.serverbackup.core.OperationHandler.formatPath;
import static de.sebli.serverbackup.utils.TaskUtils.addTask;
import static de.sebli.serverbackup.utils.TaskUtils.removeTask;

public class FileUtil {
    private static final ServerBackupPlugin instance = ServerBackupPlugin.getPluginInstance();

    private static final LogUtils logHandler = new LogUtils(instance);

    /**
     * Attempts to delete the specified file.
     *
     * <p>This method tries to delete the file at the given path. If successful, it returns {@code true}.
     * If an error occurs during deletion (e.g., the file doesn't exist or there are insufficient
     * permissions), the exception is printed to the console and the method returns {@code false}.
     *
     * @param file the file to be deleted
     * @return {@code true} if the file was successfully deleted, {@code false} otherwise
     */
    public static boolean tryDeleteFile(File file) {
        Task currentTask = addTask(TaskType.PHYSICAL, TaskPurpose.DELETE, "Deleting " + formatPath(file.getPath()));
        try {
            Files.delete(Path.of(file.getPath()));
            return true;
        } catch (IOException e) {
            logHandler.logError("Caught an exception trying to delete " + file + " file of/in path " + file.getPath(), e.getMessage(), null);
            return false;
        } finally {
            removeTask(currentTask);
        }
    }
}
