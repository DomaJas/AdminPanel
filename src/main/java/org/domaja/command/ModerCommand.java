package org.domaja.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.domaja.AdminPanel;
import org.domaja.dialog.AdminDialogs;
import org.domaja.moder.ModerManager;

import java.util.List;

public final class ModerCommand implements CommandExecutor, TabCompleter {

    private final AdminPanel plugin;
    private final ModerManager moderManager;
    private final AdminDialogs dialogs;

    public ModerCommand(AdminPanel plugin, ModerManager moderManager, AdminDialogs dialogs) {
        this.plugin = plugin;
        this.moderManager = moderManager;
        this.dialogs = dialogs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        String permission = plugin.getConfig().getString("moder-permission", "adminpanel.moder");
        if (!player.hasPermission(permission)) {
            player.sendMessage("§cУ вас нет прав на режим модерации.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            dialogs.openModeratorLevels(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("on")) {
            moderManager.enable(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("off")) {
            moderManager.disable(player);
            return true;
        }

        player.sendMessage("§eИспользование: §f/moder <on|off|info>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("on", "off", "info");
        }
        return List.of();
    }
}
