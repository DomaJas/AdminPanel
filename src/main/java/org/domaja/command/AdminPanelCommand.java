package org.domaja.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.domaja.AdminPanel;
import org.domaja.dialog.AdminDialogs;

public final class AdminPanelCommand implements CommandExecutor {

    private final AdminPanel plugin;
    private final AdminDialogs dialogs;

    public AdminPanelCommand(AdminPanel plugin, AdminDialogs dialogs) {
        this.plugin = plugin;
        this.dialogs = dialogs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        String permission = plugin.getConfig().getString("admin-permission", "adminpanel.admin");
        if (!player.hasPermission(permission)) {
            player.sendMessage("§cУ вас нет прав на админ-панель.");
            return true;
        }

        dialogs.openAdminPanel(player);
        return true;
    }
}
