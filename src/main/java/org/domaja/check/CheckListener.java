package org.domaja.check;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;

public final class CheckListener implements Listener {

    private final CheckManager manager;

    public CheckListener(CheckManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJump(PlayerJumpEvent event) {
        if (manager.isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!manager.isFrozen(player) || event.getTo() == null) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setCancelled(true);
            return;
        }

        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (manager.isFrozen(event.getPlayer()) && !manager.isInternalTeleport()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (manager.isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cВо время проверки команды недоступны.");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (manager.isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!manager.isFrozen(player)) {
            return;
        }

        String message = event.getMessage();

        if (message.startsWith("!")) {
            event.setCancelled(true);
            player.sendMessage("§cВо время проверки глобальный чат недоступен.");
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (manager.isFrozen(event.getPlayer()) || manager.isPlatformBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (manager.isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && manager.isFrozen(player)) {
            event.setCancelled(true);
        }
    }
}