package org.domaja.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.domaja.AdminPanel;
import org.domaja.check.CheckManager;
import org.domaja.moder.ModerManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CheckCommand implements CommandExecutor, TabCompleter {

    private final AdminPanel plugin;
    private final ModerManager moderManager;
    private final CheckManager checkManager;

    public CheckCommand(AdminPanel plugin, ModerManager moderManager, CheckManager checkManager) {
        this.plugin = plugin;
        this.moderManager = moderManager;
        this.checkManager = checkManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player moder)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        String permission = plugin.getConfig().getString("moder-permission", "adminpanel.moder");
        if (!moder.hasPermission(permission)) {
            error(moder, "У вас нет прав на эту команду.");
            return true;
        }
        if (!moderManager.isModerator(moder)) {
            error(moder, "Команда доступна только в режиме модерации. Включите его: /moder on");
            return true;
        }

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "proverka" -> {
                if (args.length != 1) {
                    error(moder, "Использование: /proverka <ник>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    error(moder, "Игрок не в сети.");
                    return true;
                }
                checkManager.start(moder, target);
            }
            case "approve" -> {
                if (args.length != 1) {
                    error(moder, "Использование: /approve <ник>");
                    return true;
                }
                checkManager.approve(moder, Bukkit.getPlayerExact(args[0]));
            }
            case "decline" -> {
                if (args.length < 3) {
                    error(moder, "Использование: /decline <ник> <время: 30m, 1h, 1d, 1w> <причина>");
                    return true;
                }
                Duration duration = CheckManager.parseDuration(args[1]);
                if (duration == null) {
                    error(moder, "Неверное время. Примеры: 30s, 15m, 1h, 1d, 1w");
                    return true;
                }
                String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                checkManager.decline(moder, Bukkit.getPlayerExact(args[0]), duration, reason);
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player moder)) {
            return List.of();
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if (name.equals("proverka")) {
                return Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(moder))
                        .map(Player::getName)
                        .toList();
            }
            return checkManager.targetNamesOf(moder);
        }
        if (name.equals("decline") && args.length == 2) {
            return List.of("1h", "1d", "7d", "1w");
        }
        return List.of();
    }

    private static void error(Player player, String text) {
        player.sendMessage(Component.text(text, NamedTextColor.RED));
    }
}