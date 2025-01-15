package de.sebli.serverbackup.utils;

import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.utils.enums.TaskPurpose;
import de.sebli.serverbackup.utils.enums.TaskType;
import de.sebli.serverbackup.utils.records.Task;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class TaskUtils {
    //TODO: Maybe use Collections.synchronizedList for the lists?
    private static final List<Task> tasks = new ArrayList<>();
    private static final List<String> formattedTasks = new ArrayList<>();

    // Add a task based on type, purpose, and description
    public static Task addTask(TaskType type, TaskPurpose purpose, String description) {
        // Find tasks with the same type and purpose
        int currentIndex = (int) tasks.stream()
                .filter(task -> task.type() == type && task.purpose() == purpose)
                .count() + 1;

        // Create and add the new task
        Task newTask = new Task(type, purpose, currentIndex);
        tasks.add(newTask);

        // Format the task and add to formattedTasks
        String formattedTask = type.getType() + " " + purpose + " #" + currentIndex + " {" + description + "}";
        formattedTasks.add(formattedTask);

        return newTask;
    }

    public static boolean removeTask(Task task) {
        if (tasks.remove(task)) {
            // Remove the exact formatted task
            String formattedTaskToRemove = task.type().getType() + " " + task.purpose() + " #" + task.index() + " {";
            formattedTasks.removeIf(t -> t.startsWith(formattedTaskToRemove));

            // Re-index remaining tasks for the same type and purpose
            reindexTasks(task.type(), task.purpose());

            // Log removal
            if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("SendDebugMessages")) {
                String formattedDebugMessage = MessageFormat.format(
                        "Task: {0}\nType: {1}\nPurpose: {2}\nIndex: {3}\nTask was removed successfully!",
                        task, task.type(), task.purpose(), task.index()
                );
                ServerBackupPlugin.getPluginInstance().getLogger().info(formattedDebugMessage);
            }
            return true;
        } else {
            if (ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("SendDebugMessages")) {
                String formattedDebugMessage = MessageFormat.format(
                        "Task: {0}\nType: {1}\nPurpose: {2}\nIndex: {3}\nTask not found or invalid task given, didn''t remove designated task.",
                        task, task.type(), task.purpose(), task.index()
                );
                ServerBackupPlugin.getPluginInstance().getLogger().warning(formattedDebugMessage);
            }
            return false;
        }
    }


    // Find a specific task
    public static Task findTask(TaskType type, TaskPurpose purpose, int index) {
        return tasks.stream()
                .filter(task -> task.type() == type && task.purpose() == purpose && task.index() == index)
                .findFirst()
                .orElse(null);
    }

    // Re-index tasks after removal
    private static void reindexTasks(TaskType type, TaskPurpose purpose) {
        int index = 1;
        for (Task task : tasks) {
            if (task.type() == type && task.purpose() == purpose) {
                tasks.set(tasks.indexOf(task), new Task(type, purpose, index));
                index++;
            }
        }
    }

    public static List<Task> getTasks() {
        return tasks;
    }

    public static List<String> getFormattedTasks() {
        return formattedTasks;
    }
}
