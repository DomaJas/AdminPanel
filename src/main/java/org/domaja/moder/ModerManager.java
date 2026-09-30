package org.domaja.moder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.domaja.check.CheckManager;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModerManager {

    private final JavaPlugin plugin;
    private final StateStorage storage;
    private final Map<UUID, StateStorage.StoredState> active = new ConcurrentHashMap<>();
    private final Set<UUID> healMode = ConcurrentHashMap.newKeySet();
    private CheckManager checkManager;

    public void setCheckManager(CheckManager checkManager) {
        this.checkManager = checkManager;
    }

    public ModerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.storage = new StateStorage(plugin);
    }

    public boolean isModerator(Player player) {
        StateStorage.StoredState known = active.get(player.getUniqueId());
        if (known != null) {
            return known.phase() != StateStorage.Phase.INACTIVE;
        }

        StateStorage.StoredState stored = storage.load(player.getUniqueId());
        if (stored == null || stored.phase() == StateStorage.Phase.INACTIVE) {
            return false;
        }
        active.put(player.getUniqueId(), stored);
        return true;
    }

    public boolean isHealMode(Player player) {
        return healMode.contains(player.getUniqueId());
    }

    public void setHealMode(Player player, boolean enabled) {
        if (enabled) {
            healMode.add(player.getUniqueId());
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
        } else {
            healMode.remove(player.getUniqueId());
        }
    }

    public void forgetPlayer(Player player) {
        healMode.remove(player.getUniqueId());
    }

    public void enable(Player player) {
        if (isModerator(player)) {
            player.sendMessage(Component.text("Режим модерации уже включен.", NamedTextColor.YELLOW));
            return;
        }

        if (!prepareForSafeSnapshot(player)) {
            return;
        }

        player.closeInventory();
        ModerationSnapshot snapshot = ModerationSnapshot.capture(player);
        UUID uuid = player.getUniqueId();

        try {
            storage.save(uuid, StateStorage.Phase.ACTIVE, snapshot);
            active.put(uuid, new StateStorage.StoredState(uuid, StateStorage.Phase.ACTIVE, snapshot));
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not persist moderator state for " + player.getName() + ": " + exception.getMessage());
            player.sendMessage(Component.text("Не удалось безопасно сохранить инвентарь. Режим модерации НЕ включен.", NamedTextColor.RED));
            return;
        }

        player.getInventory().clear();
        player.setAllowFlight(true);
        player.updateCommands();
        player.sendMessage(Component.text("Режим модерации включен. Ваш инвентарь сохранен.", NamedTextColor.GREEN));
    }

    public void disable(Player player) {
        StateStorage.StoredState stored = active.get(player.getUniqueId());
        if (stored == null) {
            stored = storage.load(player.getUniqueId());
        }

        if (stored == null || stored.phase() == StateStorage.Phase.INACTIVE) {
            active.remove(player.getUniqueId());
            player.sendMessage(Component.text("Режим модерации не включен.", NamedTextColor.YELLOW));
            return;
        }

        if (checkManager != null) {
            checkManager.endByModerator(player);
        }

        UUID uuid = player.getUniqueId();
        ModerationSnapshot snapshot = stored.snapshot();

        try {
            storage.save(uuid, StateStorage.Phase.RESTORING, snapshot);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not journal moderator restore for " + player.getName() + ": " + exception.getMessage());
            player.sendMessage(Component.text("Восстановление остановлено: резервная копия не записалась. Ваш инвентарь не изменен.", NamedTextColor.RED));
            return;
        }

        try {
            applySnapshot(player, snapshot);
            storage.save(uuid, StateStorage.Phase.INACTIVE, snapshot);
            storage.delete(uuid);
            active.remove(uuid);
            healMode.remove(uuid);
            player.updateCommands();

            player.sendMessage(Component.text("Режим модерации выключен. Исходный инвентарь, режим игры и позиция восстановлены.", NamedTextColor.GREEN));
        } catch (Exception exception) {
            plugin.getLogger().severe("Could not restore moderator state for " + player.getName() + ": " + exception.getMessage());
            player.sendMessage(Component.text("Не удалось завершить восстановление. Резервная копия сохранена; повторите /moder off.", NamedTextColor.RED));
        }
    }

    public void resumeAfterJoin(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            StateStorage.StoredState stored = storage.load(player.getUniqueId());
            if (stored == null) {
                return;
            }

            if (stored.phase() == StateStorage.Phase.INACTIVE) {
                active.remove(player.getUniqueId());
                storage.delete(player.getUniqueId());
                return;
            }

            active.put(player.getUniqueId(), stored);

            if (stored.phase() == StateStorage.Phase.RESTORING) {
                try {
                    applySnapshot(player, stored.snapshot());
                    storage.save(player.getUniqueId(), StateStorage.Phase.INACTIVE, stored.snapshot());
                    storage.delete(player.getUniqueId());
                    active.remove(player.getUniqueId());
                    player.updateCommands();
                    player.sendMessage(Component.text("Восстановление после сбоя завершено автоматически.", NamedTextColor.GREEN));
                } catch (Exception exception) {
                    plugin.getLogger().severe("Automatic moderator restore failed for " + player.getName() + ": " + exception.getMessage());
                    player.sendMessage(Component.text("Автоматическое восстановление не завершено. Резервная копия сохранена.", NamedTextColor.RED));
                }
                return;
            }

            player.setAllowFlight(true);
            player.updateCommands();
            player.sendMessage(Component.text("Режим модерации восстановлен после входа. Ваш исходный инвентарь по-прежнему защищен.", NamedTextColor.AQUA));
        });
    }

    public void persistActivePlayersToDisk() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isModerator(player)) {
                continue;
            }
            try {
                player.saveData();
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not save playerdata for active moderator " + player.getName() + ": " + exception.getMessage());
            }
        }
    }

    private void applySnapshot(Player player, ModerationSnapshot snapshot) {
        player.closeInventory();
        player.getInventory().clear();

        player.setGameMode(snapshot.gameMode());
        player.getInventory().setStorageContents(cloneItems(snapshot.storageContents()));
        player.getInventory().setArmorContents(cloneItems(snapshot.armorContents()));
        player.getInventory().setExtraContents(cloneItems(snapshot.extraContents()));
        player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, snapshot.heldItemSlot())));

        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.flying() && snapshot.allowFlight());
        player.setFlySpeed(snapshot.flySpeed());
        player.setWalkSpeed(snapshot.walkSpeed());
        player.setFoodLevel(Math.max(0, Math.min(20, snapshot.foodLevel())));
        player.setSaturation(Math.max(0.0F, Math.min(snapshot.saturation(), player.getFoodLevel())));
        player.setExhaustion(Math.max(0.0F, snapshot.exhaustion()));
        player.setLevel(Math.max(0, snapshot.level()));
        player.setExp(Math.max(0.0F, Math.min(1.0F, snapshot.exp())));
        player.setHealth(Math.max(0.0D, Math.min(snapshot.health(), player.getMaxHealth())));
        player.teleport(snapshot.location());
    }

    private boolean prepareForSafeSnapshot(Player player) {
        org.bukkit.inventory.InventoryView view = player.getOpenInventory();
        if (view.getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            player.sendMessage(Component.text(
                    "Закройте открытый контейнер перед включением режима модерации. Это защищает предметы в контейнерах и на курсоре.",
                    NamedTextColor.YELLOW
            ));
            return false;
        }

        org.bukkit.inventory.ItemStack cursor = view.getCursor();
        if (cursor != null && !cursor.isEmpty() && !hasFreeStorageSlot(player)) {
            player.sendMessage(Component.text(
                    "Инвентарь полностью заполнен предметом на курсоре. Освободите один слот и повторите /moder on.",
                    NamedTextColor.RED
            ));
            return false;
        }
        return true;
    }

    private boolean hasFreeStorageSlot(Player player) {
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private ItemStack[] cloneItems(ItemStack[] input) {
        ItemStack[] output = new ItemStack[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i] == null ? null : input[i].clone();
        }
        return output;
    }
}