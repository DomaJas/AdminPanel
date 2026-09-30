package org.domaja.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.domaja.AdminPanel;
import org.domaja.aura.AuraManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AuraCommand implements CommandExecutor, TabCompleter {

    private final AdminPanel plugin;
    private final AuraManager auraManager;

    public AuraCommand(AdminPanel plugin, AuraManager auraManager) {
        this.plugin = plugin;
        this.auraManager = auraManager;
    }

    private boolean isModer(Player player) {
        return player.hasPermission(plugin.getConfig().getString("moder-permission", "adminpanel.moder"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("getcode")) {
            handleGetCode(player);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("code")) {
            handleCode(player, args);
            return true;
        }

        handleVote(player, args);
        return true;
    }

    private void handleGetCode(Player moder) {
        if (!isModer(moder)) {
            moder.sendMessage("§cТолько модератор может получить код.");
            return;
        }
        String code = auraManager.createCode(moder.getUniqueId(), moder.getName());
        moder.sendMessage(Component.text("Ваш код для ауры: ", NamedTextColor.GREEN)
                .append(Component.text(code, NamedTextColor.GOLD)
                        .clickEvent(ClickEvent.copyToClipboard(code)))
                .append(Component.text(" (нажмите, чтобы скопировать)", NamedTextColor.GRAY)));
        moder.sendMessage("§7Код одноразовый и действует " + auraManager.codeTtlMinutes()
                + " мин. Передайте его игроку: §f/aura code " + code + " <plus|minus> <1-5>");
    }

    private void handleCode(Player voter, String[] args) {
        if (args.length != 4) {
            voter.sendMessage("§eИспользование: §f/aura code <код> <plus|minus> <1-5>");
            return;
        }

        AuraManager.AuraCode entry = auraManager.peekCode(args[1]);
        if (entry == null) {
            voter.sendMessage("§cКод недействителен или истёк.");
            return;
        }
        if (entry.moderator().equals(voter.getUniqueId())) {
            voter.sendMessage("§cНельзя использовать свой собственный код.");
            return;
        }

        Integer delta = parseDelta(voter, args[2], args[3]);
        if (delta == null) {
            return;
        }

        if (!auraManager.consumeCode(args[1])) {
            voter.sendMessage("§cКод недействителен или уже использован.");
            return;
        }

        int total = auraManager.addAura(entry.moderator(), entry.moderatorName(), delta);
        String sign = delta > 0 ? "+" + delta : String.valueOf(delta);

        voter.sendMessage("§aВы изменили ауру модератора " + entry.moderatorName() + " на " + sign
                + ". Текущая аура: " + total);

        Player moder = Bukkit.getPlayer(entry.moderator());
        if (moder != null) {
            moder.sendMessage("§eВаша аура изменилась на " + sign + " (по коду). Текущая аура: " + total);
        }
    }

    private void handleVote(Player voter, String[] args) {
        if (args.length != 3) {
            voter.sendMessage("§eИспользование: §f/aura <модератор> <plus|minus> <1-5>");
            voter.sendMessage("§eЕсли у вас есть код: §f/aura code <код> <plus|minus> <1-5>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !isModer(target)) {
            voter.sendMessage("§cМодератор не найден в игре.");
            return;
        }
        if (target.getUniqueId().equals(voter.getUniqueId())) {
            voter.sendMessage("§cНельзя менять ауру самому себе.");
            return;
        }

        Integer delta = parseDelta(voter, args[1], args[2]);
        if (delta == null) {
            return;
        }

        if (!auraManager.canVote(voter.getUniqueId())) {
            voter.sendMessage("§cВы уже меняли ауру сегодня. Попросите у модератора код: /aura code <код> ...");
            return;
        }

        int total = auraManager.vote(voter.getUniqueId(), target.getUniqueId(), target.getName(), delta);
        String sign = delta > 0 ? "+" + delta : String.valueOf(delta);
        voter.sendMessage("§aВы изменили ауру модератора " + target.getName() + " на " + sign
                + ". Текущая аура: " + total);
        target.sendMessage("§eВаша аура изменилась на " + sign + ". Текущая аура: " + total);
    }

    private Integer parseDelta(Player player, String typeRaw, String amountRaw) {
        String type = typeRaw.toLowerCase(Locale.ROOT);
        if (!type.equals("plus") && !type.equals("minus")) {
            player.sendMessage("§eУкажите §fplus §eили §fminus§e.");
            return null;
        }
        int amount;
        try {
            amount = Integer.parseInt(amountRaw);
        } catch (NumberFormatException exception) {
            player.sendMessage("§cЗначение должно быть числом от 1 до 5.");
            return null;
        }
        if (amount < 1 || amount > 5) {
            player.sendMessage("§cЗначение должно быть от 1 до 5.");
            return null;
        }
        return type.equals("plus") ? amount : -amount;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if (sender instanceof Player p && isModer(p) && "getcode".startsWith(prefix)) {
                result.add("getcode");
            }
            if ("code".startsWith(prefix)) {
                result.add("code");
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (isModer(online) && online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(online.getName());
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("code")) {
                return List.of("<код>");
            }
            result.addAll(List.of("plus", "minus"));
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("code")) {
                result.addAll(List.of("plus", "minus"));
            } else {
                result.addAll(List.of("1", "2", "3", "4", "5"));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("code")) {
            result.addAll(List.of("1", "2", "3", "4", "5"));
        }
        return result;
    }
}