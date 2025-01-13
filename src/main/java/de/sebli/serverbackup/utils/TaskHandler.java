package de.sebli.serverbackup.utils;

import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.utils.enums.TaskPurpose;
import de.sebli.serverbackup.utils.enums.TaskType;
import de.sebli.serverbackup.utils.records.Task;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class TaskHandler {
    private static List<Task> tasks = new ArrayList<>();
    private static List<String> formattedTasks = new ArrayList<>();

    public static Task addTask(TaskType type, TaskPurpose purpose, String description) {
        // Find the TaskIndex for the given TaskType
        Task existingIndex = findTask(type, purpose, 0);

        int currentIndex;
        if (existingIndex == null) {
            // If no index exists for this type, start from 1
            currentIndex = 1;
            // Add a new TaskIndex for this type
            tasks.add(new Task(type, purpose, currentIndex));
        } else {
            // If an index exists, increment it
            currentIndex = existingIndex.index() + 1;
            // Remove the old TaskIndex and add the updated one
            tasks.remove(existingIndex);
            tasks.add(new Task(type, purpose, currentIndex));
        }

        // Format the task with the type and index
        String formattedTask = type.getType() + " " + + " #" + currentIndex + " {" + description + " }";

        // Add the task to the list
        formattedTasks.add(formattedTask);

        // Return the updated TaskIndex
        return new Task(type, purpose, currentIndex);
    }

    public static void removeTask(Task task) {
        if (task.index() >= 0 && task.index() < formattedTasks.size()) {
            // Remove the task at the specified index
            formattedTasks.remove(task.index());
            if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("SendDebugMessages")) {
                ServerBackupPlugin.getPluginInstance().getLogger().info(MessageFormat.format(
                        "Task: {0}\nType: {1}\nPurpose: {2}\nIndex: {3} was removed successfully.",
                       task, task.type(), task.purpose(), task.index()
                ));
            }
        } else {
            if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("SendDebugMessages")) {
                ServerBackupPlugin.getPluginInstance().getLogger().warning(
                        "Task not found or invalid task given, didn't remove designated task");
            }
        }
    }

    private static Task findTask(TaskType type, TaskPurpose purpose, int index) {
        boolean correctType;
        boolean correctPurpose;
        boolean correctIndex;

        for (Task task : tasks) {
            correctType = task.type() == type;

            correctPurpose = task.purpose() == purpose;

            correctIndex = task.index() == index;

            if (correctType && correctPurpose && correctIndex) {
                return task;
            }
        }
        return null;
    }
}
