package org.domaja.moder;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class StateStorage {

    public enum Phase {
        ACTIVE,
        RESTORING,
        INACTIVE
    }

    public record StoredState(UUID uuid, Phase phase, ModerationSnapshot snapshot) {
    }

    private final JavaPlugin plugin;
    private final File stateFolder;

    public StateStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.stateFolder = new File(plugin.getDataFolder(), "moderator-states");
        if (!stateFolder.exists() && !stateFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create moderator state folder: " + stateFolder);
        }
    }

    public synchronized void save(UUID uuid, Phase phase, ModerationSnapshot snapshot) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format", 1);
        yaml.set("uuid", uuid.toString());
        yaml.set("phase", phase.name());

        saveLocation(yaml, snapshot.location());
        yaml.set("gamemode", snapshot.gameMode().name());
        yaml.set("held-item-slot", snapshot.heldItemSlot());
        yaml.set("health", snapshot.health());
        yaml.set("food-level", snapshot.foodLevel());
        yaml.set("saturation", snapshot.saturation());
        yaml.set("exhaustion", snapshot.exhaustion());
        yaml.set("level", snapshot.level());
        yaml.set("exp", snapshot.exp());
        yaml.set("allow-flight", snapshot.allowFlight());
        yaml.set("flying", snapshot.flying());
        yaml.set("fly-speed", snapshot.flySpeed());
        yaml.set("walk-speed", snapshot.walkSpeed());
        saveItems(yaml, "inventory.storage", snapshot.storageContents());
        saveItems(yaml, "inventory.armor", snapshot.armorContents());
        saveItems(yaml, "inventory.extra", snapshot.extraContents());

        String serialized = yaml.saveToString();
        writeAtomic(file(uuid), serialized);
        writeAtomic(backupFile(uuid), serialized);
    }

    public synchronized StoredState load(UUID expectedUuid) {
        File primary = file(expectedUuid);
        File backup = backupFile(expectedUuid);

        StoredState result = tryLoad(expectedUuid, primary);
        if (result != null) {
            return result;
        }
        result = tryLoad(expectedUuid, backup);
        if (result != null) {
            plugin.getLogger().warning("Recovered moderator state for " + expectedUuid + " from backup.");
        }
        return result;
    }

    public synchronized void delete(UUID uuid) {
        deleteQuietly(file(uuid));
        deleteQuietly(backupFile(uuid));
    }

    private StoredState tryLoad(UUID expectedUuid, File file) {
        if (!file.isFile()) {
            return null;
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            UUID storedUuid = UUID.fromString(yaml.getString("uuid", ""));
            if (!storedUuid.equals(expectedUuid)) {
                throw new IOException("UUID mismatch");
            }

            Phase phase = Phase.valueOf(yaml.getString("phase", "ACTIVE"));
            ModerationSnapshot snapshot = loadSnapshot(yaml);
            return new StoredState(storedUuid, phase, snapshot);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to load moderator state " + file.getName() + ": " + exception.getMessage());
            return null;
        }
    }

    private ModerationSnapshot loadSnapshot(YamlConfiguration yaml) throws IOException {
        World world = resolveWorld(yaml.getString("location.world-uuid"), yaml.getString("location.world-name"));
        if (world == null) {
            throw new IOException("Saved world is not loaded");
        }

        Location location = new Location(
                world,
                yaml.getDouble("location.x"),
                yaml.getDouble("location.y"),
                yaml.getDouble("location.z"),
                (float) yaml.getDouble("location.yaw"),
                (float) yaml.getDouble("location.pitch")
        );

        GameMode gameMode = GameMode.valueOf(yaml.getString("gamemode", GameMode.SURVIVAL.name()));
        ItemStack[] storage = loadItems(yaml, "inventory.storage", 36);
        ItemStack[] armor = loadItems(yaml, "inventory.armor", 4);
        ItemStack[] extra = loadItems(yaml, "inventory.extra", 1);

        return new ModerationSnapshot(
                location,
                gameMode,
                storage,
                armor,
                extra,
                yaml.getInt("held-item-slot", 0),
                yaml.getDouble("health", 20.0D),
                yaml.getInt("food-level", 20),
                (float) yaml.getDouble("saturation", 5.0D),
                (float) yaml.getDouble("exhaustion", 0.0D),
                yaml.getInt("level", 0),
                (float) yaml.getDouble("exp", 0.0D),
                yaml.getBoolean("allow-flight", false),
                yaml.getBoolean("flying", false),
                (float) yaml.getDouble("fly-speed", 0.1D),
                (float) yaml.getDouble("walk-speed", 0.2D)
        );
    }

    private ItemStack[] loadItems(YamlConfiguration yaml, String path, int defaultLength) {
        int length = yaml.getInt(path + "._size", defaultLength);
        ItemStack[] items = new ItemStack[Math.max(0, length)];
        for (int i = 0; i < items.length; i++) {
            ItemStack item = yaml.getItemStack(path + "." + i);
            items[i] = item == null ? null : item.clone();
        }
        return items;
    }

    private void saveItems(YamlConfiguration yaml, String path, ItemStack[] items) {
        yaml.set(path + "._size", items.length);
        for (int i = 0; i < items.length; i++) {
            yaml.set(path + "." + i, items[i] == null ? null : items[i].clone());
        }
    }

    private void saveLocation(YamlConfiguration yaml, Location location) {
        yaml.set("location.world-uuid", location.getWorld() == null ? null : location.getWorld().getUID().toString());
        yaml.set("location.world-name", location.getWorld() == null ? null : location.getWorld().getName());
        yaml.set("location.x", location.getX());
        yaml.set("location.y", location.getY());
        yaml.set("location.z", location.getZ());
        yaml.set("location.yaw", location.getYaw());
        yaml.set("location.pitch", location.getPitch());
    }

    private World resolveWorld(String uuidString, String name) {
        if (uuidString != null && !uuidString.isBlank()) {
            try {
                World world = Bukkit.getWorld(UUID.fromString(uuidString));
                if (world != null) {
                    return world;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return name == null || name.isBlank() ? null : Bukkit.getWorld(name);
    }

    private void writeAtomic(File target, String content) throws IOException {
        if (!stateFolder.exists() && !stateFolder.mkdirs()) {
            throw new IOException("Could not create " + stateFolder);
        }

        File temp = new File(stateFolder, target.getName() + ".tmp");
        Files.writeString(
                temp.toPath(),
                content,
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE
        );

        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private File file(UUID uuid) {
        return new File(stateFolder, uuid + ".yml");
    }

    private File backupFile(UUID uuid) {
        return new File(stateFolder, uuid + ".bak.yml");
    }

    private void deleteQuietly(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to delete " + file.getName() + ": " + exception.getMessage());
        }
    }
}
