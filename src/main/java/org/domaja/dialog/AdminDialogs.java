package org.domaja.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.domaja.AdminPanel;
import org.domaja.moder.ModerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AdminDialogs {

    private final AdminPanel plugin;
    private final ModerManager moderManager;

    public AdminDialogs(AdminPanel plugin, ModerManager moderManager) {
        this.plugin = plugin;
        this.moderManager = moderManager;
    }

    public void openAdminPanel(Player player) {
        if (!player.hasPermission(plugin.getConfig().getString("admin-permission", "adminpanel.admin"))) {
            player.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
            return;
        }
        if (!moderManager.isModerator(player)) {
            player.sendMessage(Component.text("Админ-панель доступна только в режиме модерации. Включите его: /moder on", NamedTextColor.RED));
            return;
        }

        boolean heal = moderManager.isHealMode(player);
        List<ActionButton> actions = new ArrayList<>();

        actions.add(button("Выключить режим модерации", "Восстановить сохранённое состояние", null,
                () -> {
                    moderManager.disable(player);
                    player.closeDialog();
                }));

        actions.add(button("Уровни модераторов", "Команды и действия для каждого уровня", null,
                () -> openModeratorLevels(player)));

        actions.add(button("Лечение и насыщение: " + (heal ? "ВКЛ" : "ВЫКЛ"),
                "Пока включено: здоровье и сытость всегда полные",
                heal ? NamedTextColor.GREEN : NamedTextColor.RED,
                () -> {
                    boolean enable = !moderManager.isHealMode(player);
                    moderManager.setHealMode(player, enable);
                    player.sendMessage(Component.text(
                            enable ? "Лечение и насыщение включено." : "Лечение и насыщение выключено.",
                            enable ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                    reopen(player);
                }));

        actions.add(button("Полёт: " + (player.getAllowFlight() ? "ВКЛ" : "ВЫКЛ"), "Переключить право на полёт", null,
                () -> {
                    boolean enabled = !player.getAllowFlight();
                    player.setAllowFlight(enabled);
                    player.setFlying(enabled);
                    reopen(player);
                }));

        actions.add(button("Ваниш", "Скрыть себя от обычных игроков", null,
                () -> player.performCommand("v")));

        Dialog dialog = Dialog.create(builder -> builder
                .empty()
                .base(DialogBase.builder(bold("ADMIN PANEL"))
                        .externalTitle(bold("Админ-панель"))
                        .canCloseWithEscape(true)
                        .body(List.of(
                                DialogBody.plainMessage(Component.text(
                                        "Центр управления модератора\n\n" +
                                                "Игровой режим: " + player.getGameMode().name() + "\n" +
                                                "Лечение и насыщение: " + (heal ? "ВКЛ" : "ВЫКЛ")), 256)
                        ))
                        .build())
                .type(DialogType.multiAction(actions, button("Закрыть", "Закрыть панель", null, player::closeDialog), 2))
        );

        player.showDialog(dialog);
    }

    public void openModeratorLevels(Player player) {
        List<ActionButton> levels = new ArrayList<>();
        var section = plugin.getConfig().getConfigurationSection("moderator-levels");
        if (section != null) {
            Set<String> keys = section.getKeys(false);
            keys.stream().sorted((a, b) -> Integer.compare(parseInt(a), parseInt(b))).forEach(key -> {
                String name = section.getString(key + ".display-name", "Модератор " + key + " уровня");
                String description = section.getString(key + ".description", "");
                levels.add(button(name, description, null,
                        () -> openModeratorLevel(player, key, name, description)));
            });
        }

        if (levels.isEmpty()) {
            levels.add(button("Нет настроенных уровней", "Добавьте moderator-levels в config.yml", null,
                    () -> openModeratorLevels(player)));
        }

        ActionButton exit = button("Назад", "Вернуться", null, () -> backToPanel(player));

        Dialog dialog = Dialog.create(builder -> builder
                .empty()
                .base(DialogBase.builder(bold("MODERATOR LEVELS"))
                        .externalTitle(Component.text("Уровни модераторов"))
                        .body(List.of(
                                DialogBody.plainMessage(Component.text("Справочник прав и обязанностей модерации."), 500),
                                DialogBody.plainMessage(Component.text("Нажмите на уровень, чтобы посмотреть полный список команд и действий."), 500)
                        ))
                        .build())
                .type(DialogType.multiAction(levels, exit, 2))
        );

        player.showDialog(dialog);
    }

    private void openModeratorLevel(Player player, String key, String name, String description) {
        List<String> actions = plugin.getConfig().getStringList("moderator-levels." + key + ".actions");

        Component details = Component.text("Уровень: ")
                .append(bold(name))
                .append(Component.newline())
                .append(Component.text(description))
                .append(Component.newline()).append(Component.newline())
                .append(bold("Доступные действия:"))
                .append(Component.newline());

        for (String action : actions) {
            details = details.append(Component.text("• " + action)).append(Component.newline());
        }
        final Component finalDetails = details;

        Dialog dialog = Dialog.create(builder -> builder
                .empty()
                .base(DialogBase.builder(bold(name))
                        .externalTitle(Component.text(name))
                        .body(List.of(
                                DialogBody.plainMessage(finalDetails, 560)
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                        button("Назад", "Вернуться к уровням", null, () -> openModeratorLevels(player)),
                        button("Закрыть", "Закрыть справочник", null, player::closeDialog)
                ), null, 2))
        );

        player.showDialog(dialog);
    }

    private void backToPanel(Player player) {
        if (moderManager.isModerator(player)) {
            openAdminPanel(player);
        } else {
            player.closeDialog();
        }
    }

    private static Component bold(String text) {
        return Component.text(text).decorate(TextDecoration.BOLD);
    }

    private ActionButton button(String label, String tooltip, NamedTextColor color, Runnable action) {
        Component text = bold(label);
        if (color != null) {
            text = text.color(color);
        }
        return ActionButton.builder(text)
                .tooltip(Component.text(tooltip))
                .width(220)
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player) {
                                action.run();
                            }
                        },
                        ClickCallback.Options.builder().uses(1).build()
                ))
                .build();
    }

    private void reopen(Player player) {
        player.closeDialog();
        Bukkit.getScheduler().runTask(plugin, () -> openAdminPanel(player));
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private static Material material(String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}