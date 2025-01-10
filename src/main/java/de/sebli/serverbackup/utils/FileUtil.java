package de.sebli.serverbackup.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class FileUtil {

    private FileUtil() {
        throw new IllegalStateException("Utility class");
    }

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
        try  {
            Files.delete(Path.of(file.getPath()));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
