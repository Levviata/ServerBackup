package de.sebli.serverbackup;

import de.sebli.serverbackup.commands.Executor;
import de.sebli.serverbackup.commands.ServerBackupTabCompleter;
import de.sebli.serverbackup.core.DynamicBackup;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.listeners.JoinListener;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public class ServerBackupPlugin extends JavaPlugin { // Singleton implementation

    private static ServerBackupPlugin pluginInstance;

    @Override
    public void onDisable() {
        OperationHandler.stopTimer();

        for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            task.cancel();

            this.getLogger().warning("WARNING - ServerBackup: Task [" + task.getTaskId()
                    + "] cancelled due to server shutdown. There might be some unfinished Backups.");
        }

        Bukkit.getLogger().info("ServerBackup: Plugin disabled.");
    }

    @Override
    public void onEnable() {
        setPluginInstance(this);

        Configuration.loadUp();

        OperationHandler.startTimer();

        Objects.requireNonNull(getCommand("backup")).setExecutor(new Executor());
        Objects.requireNonNull(getCommand("backup")).setTabCompleter(new ServerBackupTabCompleter());

        Bukkit.getPluginManager().registerEvents(new JoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new DynamicBackup(), this);

        Bukkit.getLogger().info("Plugin enabled.");

        if (getConfig().getBoolean("UpdateAvailableMessage")) {
            OperationHandler.checkVersion();
        }
    }
    private static void setPluginInstance(ServerBackupPlugin instanceIn) {
        pluginInstance = instanceIn;
    }

    public static ServerBackupPlugin getPluginInstance() {
        return pluginInstance;
    }

    public static void sendMessageWithLogs(String message, CommandSender senderIn) {
        if (senderIn instanceof ConsoleCommandSender) {
            senderIn.sendMessage(message);
        } else { // Command block or player
            senderIn.sendMessage(message);

            String formattedMessage =
                    "\n" +
                    "[START OF COMMAND LOGGER] " + "\n" +
                    "[INFO] Executed by player " + senderIn.getName() + "\n" +
                    "[INFO] Logged information: " + message + "\n" +
                    "[END OF COMMAND LOGGER]";

            ServerBackupPlugin.getPluginInstance().getLogger().info(formattedMessage);
        }
    }
}
