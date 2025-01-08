package de.sebli.serverbackup;

import de.sebli.serverbackup.commands.Executor;
import de.sebli.serverbackup.commands.TabCompleter;
import de.sebli.serverbackup.core.DynamicBackup;
import de.sebli.serverbackup.core.OperationHandler;
import de.sebli.serverbackup.listeners.JoinListener;
import io.github.cdimascio.dotenv.Dotenv;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

public class ServerBackupPlugin extends JavaPlugin { // Singleton implementation

    private ServerBackupPlugin pluginInstance;

    private Dotenv envKeys;

    @Override
    public void onDisable() {
        OperationHandler.stopTimer();

        for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            task.cancel();

            this.getLogger().warning("WARNING - ServerBackup: Task [" + task.getTaskId()
                    + "] cancelled due to server shutdown. There might be some unfinished Backups.");
        }

        Bukkit.getLogger().log(Level.INFO, "ServerBackup: Plugin disabled.");
    }

    @Override
    public void onEnable() {
        pluginInstance = this;

        Configuration.loadUp();

        OperationHandler.startTimer();

        getCommand("backup").setExecutor(new Executor());
        getCommand("backup").setTabCompleter(new TabCompleter());

        Bukkit.getPluginManager().registerEvents(new JoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new DynamicBackup(), this);

        envKeys = Dotenv.load(); // Load our env keys

        Bukkit.getLogger().info("Plugin enabled.");

        if (getConfig().getBoolean("UpdateAvailableMessage")) {
            OperationHandler.checkVersion();
        }
    }

    public ServerBackupPlugin getPluginInstance() {
        return pluginInstance;
    }

    public Dotenv getEnvKey() {
        return envKeys;
    }
}
