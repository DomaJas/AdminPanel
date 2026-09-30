package org.domaja.listener;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.domaja.AdminPanel;
import org.domaja.check.CheckManager;
import org.domaja.moder.ModerManager;

import java.util.*;

public final class PlayerListener implements Listener {

    private final AdminPanel plugin;
    private final ModerManager moderManager;
    private final Set<String> moderOnlyCommands = new HashSet<>();
    private final List<String> moderBlocked = new ArrayList<>();
    private final CheckManager checkManager;

    public PlayerListener(AdminPanel plugin, ModerManager moderManager, CheckManager checkManager) {
        this.plugin = plugin;
        this.moderManager = moderManager;
        this.checkManager = checkManager;
        reloadRestrictedCommands();
    }

    public void reloadRestrictedCommands() {
        moderOnlyCommands.clear();
        for (String raw : plugin.getConfig().getStringList("moder-only-commands")) {
            String name = normalize(raw);
            if (!name.isEmpty()) {
                moderOnlyCommands.add(name);
            }
        }

        moderBlocked.clear();
        for (String raw : plugin.getConfig().getStringList("moder-blocked-commands")) {
            String n = normalizeFull(raw);
            if (!n.isEmpty()) {
                moderBlocked.add(n);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        moderManager.resumeAfterJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        checkManager.handleQuit(player);
        if (moderManager.isModerator(player)) {
            player.saveData();
        }
        moderManager.forgetPlayer(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        boolean moder = moderManager.isModerator(player);

        if (!moder) {
            if (!moderOnlyCommands.isEmpty() && isRestricted(extractLabel(event.getMessage()))) {
                event.setCancelled(true);
                player.sendMessage("§cЭта команда доступна только в режиме модерации.");
            }
            return;
        }

        if (isBlockedForModer(event.getMessage())) {
            event.setCancelled(true);
            player.sendMessage("§cЭта команда запрещена в режиме модерации.");
        }
    }

    private boolean isBlockedForModer(String message) {
        if (moderBlocked.isEmpty()) {
            return false;
        }
        String full = normalizeFull(message);
        for (String blocked : moderBlocked) {
            if (full.equals(blocked) || full.startsWith(blocked + " ")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeFull(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        s = s.replaceAll("\\s+", " ");
        int space = s.indexOf(' ');
        String label = space == -1 ? s : s.substring(0, space);
        String rest = space == -1 ? "" : s.substring(space);

        int colon = label.indexOf(':');
        if (colon != -1) {
            label = label.substring(colon + 1);
        }
        Command cmd = Bukkit.getCommandMap().getCommand(label);
        if (cmd != null) {
            label = cmd.getName().toLowerCase(Locale.ROOT);
        }
        return label + rest;
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (moderOnlyCommands.isEmpty() || moderManager.isModerator(event.getPlayer())) {
            return;
        }
        event.getCommands().removeIf(this::isRestricted);
    }

    private boolean isRestricted(String label) {
        String name = normalize(label);
        if (moderOnlyCommands.contains(name)) {
            return true;
        }
        Command command = Bukkit.getCommandMap().getCommand(name);
        if (command != null) {
            if (moderOnlyCommands.contains(command.getName().toLowerCase(Locale.ROOT))) {
                return true;
            }
            for (String alias : command.getAliases()) {
                if (moderOnlyCommands.contains(alias.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String extractLabel(String message) {
        String text = message.startsWith("/") ? message.substring(1) : message;
        int space = text.indexOf(' ');
        return space == -1 ? text : text.substring(0, space);
    }

    private static String normalize(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        int colon = s.indexOf(':');
        return colon == -1 ? s : s.substring(colon + 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && moderManager.isHealMode(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && moderManager.isHealMode(player)) {
            event.setCancelled(true);
        }
    }
}