package de.sebli.serverbackup.utils;

import de.sebli.serverbackup.ServerBackupPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import javax.annotation.Nullable;

public class LogUtils {

    private final ServerBackupPlugin plugin;


    public LogUtils(ServerBackupPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Logs an informational message, optionally sending it to the command sender.
     *
     * <p>This method is used to log information and if a {@code sender} is specified also send the message to them.
     * Typically, this method is used for informing about a successful operation (e.g: Successful backup).
     *
     * @param message The informational message to be logged. This message should provide useful context
     *                about the operation being logged.
     * @param sender The source of the command triggering this log entry. This can be {@code null} if there is
     *               no specific sender. If {@code sender} is an instance of {@link ConsoleCommandSender} or {@code null}, the
     *               message is logged directly to the console.
     *
     * <p><strong>Examples:</strong></p>
     * <pre>
     * {@code
     * logInfo("Backup completed successfully.", playerSender);
     * logInfo("Server shutting down.", consoleSender);
     * logInfo("Configuration reloaded.", null);
     * }
     * </pre>
     */
    public void logInfo(String message, @Nullable CommandSender sender) {
        if (sender != null) {
            if (sender instanceof ConsoleCommandSender) {
                plugin.getLogger().info(message);
            } else {
                sender.sendMessage(message);
                plugin.getLogger().info("[Command Triggered Log]: " + message);
            }
        } else plugin.getLogger().info(message);
    }

    /**
     * Logs a feedback message related to a command, optionally sending it to the command sender.
     *
     * <p>This method is used to provide feedback to a command sender, typically for errors
     * or incorrect command usage. The message is logged to the plugin's logger and, if a sender
     * is provided, the message is also sent to them.</p>
     *
     * @param errorMessage A descriptive message explaining the error or feedback to be logged.
     * @param sender The source of the command triggering this log entry. This can be {@code null}
     *               if there is no specific sender. If {@code sender} is an instance of
     *               {@link ConsoleCommandSender} or {@code null} the message is logged directly to the console.
     *
     * <p><strong>Examples:</strong></p>
     * <pre>
     * {@code
     * logCommandFeedback("You do not have permission to execute this command.", playerSender);
     * logCommandFeedback("Invalid command syntax.", consoleSender);
     * logCommandFeedback("Command execution failed.", null);
     * }
     * </pre>
     */
    public void logCommandFeedback(String errorMessage, CommandSender sender) {
        if (sender != null) {
            if (sender instanceof ConsoleCommandSender) {
                plugin.getLogger().info(errorMessage);
            } else {
                sender.sendMessage("&cError. " + errorMessage);
                plugin.getLogger().info("[Command Triggered Log]: " + errorMessage);
            }
        } else plugin.getLogger().info(errorMessage);
    }

    /**
     * Logs an error message, usually an exception. Optionally sends to a command sender if provided
     *
     * <p>This method is used to report errors/exceptions encountered during plugin operations.
     * If a {@code sender} is provided and is not the console, they will receive a prompt
     * to check the console for additional details. The message is always logged to the plugin's
     * logger, with details from the provided exception message, if applicable.</p>
     *
     * @param infoMessage A brief description of the error or context in which it occurred.
     *                    This should provide enough detail to understand the nature of the error.
     * @param exceptionMessage The exception message providing additional context about the error.
     *                         This can be empty if no specific exception message is available.
     * @param sender The source of the command triggering this log entry. This can be {@code null}
     *               if no specific sender is associated with the error. If {@code sender} is an
     *               instance of {@link ConsoleCommandSender}, the message is logged directly to the console.
     *
     * <p><strong>Examples:</strong></p>
     * <pre>
     * {@code
     * logError("Failed to load configuration", e.getMessage(), playerSender);
     * logError("Player data not found", "", consoleSender);
     * logError("Unexpected error occurred", e.getMessage(), null);
     * }
     * </pre>
     */
    public void logError(String infoMessage, String exceptionMessage, @Nullable CommandSender sender) {
        String message = infoMessage + ": " + exceptionMessage;

        if (exceptionMessage.isEmpty()) message = infoMessage + ".";

        if (sender != null) {
            if (sender instanceof ConsoleCommandSender) {
                plugin.getLogger().warning(message);
            } else {
                sender.sendMessage(infoMessage + ". Check console for more information.");
                plugin.getLogger().warning(message);
            }
        } else plugin.getLogger().warning(message);
    }
}
