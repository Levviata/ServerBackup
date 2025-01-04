package de.sebli.serverbackup.utils;

/**
 * A utility class for hosting constants that are used in more than two files across the ServerBackup plugin.
 */

public class GlobalConstants {

    private GlobalConstants (){
      throw new IllegalStateException("Utility class");
    }

    public static final int RESOURCE_ID = 79320;
    public static final String FILE_NAME_PLACEHOLDER = "%file%";

    // TODO: Convert all config entries into constants
    public static final String CONFIG_BACKUP_DESTINATION = "BackupDestination";
}