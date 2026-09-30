package org.domaja.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.domaja.AdminPanel;
import org.domaja.report.ReportManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ReportCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_REASON_LENGTH = 200;

    private final AdminPanel plugin;
    private final ReportManager reportManager;

    public ReportCommand(AdminPanel plugin, ReportManager reportManager) {
        this.plugin = plugin;
        this.reportManager = reportManager;
    }

    private String moderPermission() {
        return plugin.getConfig().getString("moder-permission", "adminpanel.moder");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("done")) {
            handleDone(player, args);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            handleList(player);
            return true;
        }

        handleReport(player, args);
        return true;
    }

    private void handleReport(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "Использование: /report <ник или -> <причина>", NamedTextColor.YELLOW);
            msg(player, "Если ника нет, вместо него напишите «-».", NamedTextColor.GRAY);
            return;
        }

        long left = reportManager.remainingSeconds(player.getUniqueId());
        if (left > 0) {
            msg(player, "Репорт можно отправлять раз в " + (reportManager.cooldownMillis() / 60_000L)
                    + " мин. Подождите ещё " + formatSeconds(left) + ".", NamedTextColor.RED);
            return;
        }

        String rawTarget = args[0];
        String target;
        if (rawTarget.equals("-") || rawTarget.equalsIgnoreCase("none")
                || rawTarget.equalsIgnoreCase("безника") || rawTarget.equalsIgnoreCase("нет")) {
            target = null;
        } else {
            if (rawTarget.equalsIgnoreCase(player.getName())) {
                msg(player, "Нельзя отправить репорт на самого себя.", NamedTextColor.RED);
                return;
            }
            target = rawTarget;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (reason.isEmpty()) {
            msg(player, "Укажите причину репорта.", NamedTextColor.RED);
            return;
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            msg(player, "Причина слишком длинная (максимум " + MAX_REASON_LENGTH + " символов).", NamedTextColor.RED);
            return;
        }

        ReportManager.Report report = reportManager.create(player, target, reason);
        String targetText = target == null ? "Без ника" : target;

        Component notice = Component.text()
                .append(Component.text("[Репорт #" + report.id() + "] ", NamedTextColor.RED))
                .append(Component.text(player.getName(), NamedTextColor.GOLD))
                .append(Component.text(" → ", NamedTextColor.GRAY))
                .append(Component.text(targetText, NamedTextColor.YELLOW))
                .append(Component.text(" | Причина: ", NamedTextColor.GRAY))
                .append(Component.text(reason, NamedTextColor.WHITE))
                .append(Component.text(" [Закрыть]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.suggestCommand("/report done " + report.id()))
                        .hoverEvent(HoverEvent.showText(Component.text("/report done " + report.id()))))
                .build();

        int online = 0;
        String permission = moderPermission();
        for (Player online_ : Bukkit.getOnlinePlayers()) {
            if (online_.hasPermission(permission)) {
                online_.sendMessage(notice);
                online++;
            }
        }

        msg(player, "Репорт #" + report.id() + " отправлен."
                        + (online == 0 ? " Сейчас нет модераторов онлайн, но репорт сохранён." : ""),
                NamedTextColor.GREEN);
    }

    private void handleDone(Player moder, String[] args) {
        if (!moder.hasPermission(moderPermission())) {
            msg(moder, "У вас нет прав на эту команду.", NamedTextColor.RED);
            return;
        }
        if (args.length != 2) {
            msg(moder, "Использование: /report done <ID>", NamedTextColor.YELLOW);
            return;
        }

        int id;
        try {
            id = Integer.parseInt(args[1].replace("#", ""));
        } catch (NumberFormatException exception) {
            msg(moder, "ID должен быть числом.", NamedTextColor.RED);
            return;
        }

        ReportManager.Report report = reportManager.close(id);
        if (report == null) {
            msg(moder, "Репорт #" + id + " не найден или уже закрыт.", NamedTextColor.RED);
            return;
        }

        msg(moder, "Репорт #" + id + " закрыт.", NamedTextColor.GREEN);

        // Остальным модераторам — чтобы не дублировали работу
        String permission = moderPermission();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(moder) && online.hasPermission(permission)) {
                msg(online, "Репорт #" + id + " закрыт модератором " + moder.getName() + ".", NamedTextColor.GRAY);
            }
        }

        Player reporter = Bukkit.getPlayer(report.reporter());
        if (reporter != null) {
            msg(reporter, "Ваш репорт #" + id + " рассмотрен. Спасибо!", NamedTextColor.GREEN);
        }
    }

    private void handleList(Player moder) {
        if (!moder.hasPermission(moderPermission())) {
            msg(moder, "У вас нет прав на эту команду.", NamedTextColor.RED);
            return;
        }
        List<ReportManager.Report> open = reportManager.open();
        if (open.isEmpty()) {
            msg(moder, "Открытых репортов нет.", NamedTextColor.GREEN);
            return;
        }
        msg(moder, "Открытые репорты (" + open.size() + "):", NamedTextColor.YELLOW);
        for (ReportManager.Report r : open) {
            msg(moder, "#" + r.id() + " " + r.reporterName() + " → "
                    + (r.target() == null ? "Без ника" : r.target()) + ": " + r.reason(), NamedTextColor.GRAY);
        }
    }

    private static String formatSeconds(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return m > 0 ? m + " мин " + s + " сек" : s + " сек";
    }

    private static void msg(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!(sender instanceof Player player)) {
            return result;
        }
        boolean moder = player.hasPermission(moderPermission());

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if (moder) {
                if ("done".startsWith(prefix)) result.add("done");
                if ("list".startsWith(prefix)) result.add("list");
            }
            if ("-".startsWith(prefix)) result.add("-");
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(online.getName());
                }
            }
        } else if (args.length == 2 && moder && args[0].equalsIgnoreCase("done")) {
            for (ReportManager.Report r : reportManager.open()) {
                result.add(String.valueOf(r.id()));
            }
        }
        return result;
    }
}