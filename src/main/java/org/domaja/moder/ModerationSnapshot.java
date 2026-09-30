package org.domaja.moder;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record ModerationSnapshot(
        Location location,
        GameMode gameMode,
        ItemStack[] storageContents,
        ItemStack[] armorContents,
        ItemStack[] extraContents,
        int heldItemSlot,
        double health,
        int foodLevel,
        float saturation,
        float exhaustion,
        int level,
        float exp,
        boolean allowFlight,
        boolean flying,
        float flySpeed,
        float walkSpeed
) {

    public static ModerationSnapshot capture(Player player) {
        return new ModerationSnapshot(
                player.getLocation().clone(),
                player.getGameMode(),
                cloneItems(player.getInventory().getStorageContents()),
                cloneItems(player.getInventory().getArmorContents()),
                cloneItems(player.getInventory().getExtraContents()),
                player.getInventory().getHeldItemSlot(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getLevel(),
                player.getExp(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getFlySpeed(),
                player.getWalkSpeed()
        );
    }

    private static ItemStack[] cloneItems(ItemStack[] input) {
        ItemStack[] result = new ItemStack[input.length];
        for (int i = 0; i < input.length; i++) {
            result[i] = input[i] == null ? null : input[i].clone();
        }
        return result;
    }
}
