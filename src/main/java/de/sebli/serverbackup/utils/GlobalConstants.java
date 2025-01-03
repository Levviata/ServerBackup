package de.sebli.serverbackup.utils;

/**
 * A utility class for hosting constants that are used in more than two files across the ServerBackup plugin.
 */

public class GlobalConstants {

    private GlobalConstants (){
      throw new IllegalStateException("Utility class");
    }

    public static final String FILE_NAME_PLACEHOLDER = "%file%";
    public static final String BACKUP_DESTINATION = "BackupDestination";
}
