package de.sebli.serverbackup.utils.enums;

public enum TaskPurpose {
    CREATE("CREATE"),
    DELETE("DELETE"),
    UPLOAD("UPLOAD"),
    ZIP("ZIP"),
    PROGRESS("PROGRESS");

    private final String purpose;

    TaskPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getPurpose() {
        return this.purpose;
    }
}
