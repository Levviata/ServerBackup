package de.sebli.serverbackup.utils.enums;

public enum TaskType {
    PHYSICAL("PHSYICAL"),
    FTP("FTP"),
    FTPS("FTPS"),
    DROPBOX("DROPBOX");


    private final String type;

    TaskType(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }
}
