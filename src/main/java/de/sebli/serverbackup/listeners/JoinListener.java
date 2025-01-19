package de.sebli.serverbackup.listeners;

import de.sebli.serverbackup.ServerBackupPlugin;
import de.sebli.serverbackup.core.OperationHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class JoinListener implements Listener {

    private static final Set<UUID> checkedPlayers = new HashSet<>();

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // Check if the player has update perms >
        // check if we already told the player to update >
        // add player uuid to checked players so the update message isnt sent every join >
        // tell player to update or not
        if (p.hasPermission("backup.update") &&
                ServerBackupPlugin.getPluginInstance().getConfig().getBoolean("UpdateAvailableMessage") && !checkedPlayers.contains(p.getUniqueId())
        ) {
                checkedPlayers.add(p.getUniqueId());

                Bukkit.getScheduler().runTaskAsynchronously(ServerBackupPlugin.getPluginInstance(), () -> {
                    OperationHandler.checkVersion(p);
                });
            }
    }
}
