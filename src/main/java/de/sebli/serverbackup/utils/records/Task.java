package de.sebli.serverbackup.utils.records;

import de.sebli.serverbackup.utils.enums.TaskPurpose;
import de.sebli.serverbackup.utils.enums.TaskType;

public record Task(TaskType type, TaskPurpose purpose, int index) {
}
