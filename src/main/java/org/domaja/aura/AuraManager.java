package org.domaja.aura;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.domaja.AdminPanel;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

public final class AuraManager {

    public record AuraEntry(UUID id, String name, int value) {
    }

    private final AdminPanel plugin;
    private final File file;
    private final Map<UUID, Integer> aura = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, LocalDate> lastVote = new HashMap<>();

    public AuraManager(AdminPanel plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "aura.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection auraSection = yaml.getConfigurationSection("aura");
        if (auraSection != null) {
            for (String key : auraSection.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    aura.put(id, auraSection.getInt(key + ".value"));
                    names.put(id, auraSection.getString(key + ".name", "unknown"));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection votes = yaml.getConfigurationSection("votes");
        if (votes != null) {
            for (String key : votes.getKeys(false)) {
                try {
                    lastVote.put(UUID.fromString(key), LocalDate.parse(votes.getString(key, "")));
                } catch (Exception ignored) {
                }
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : aura.entrySet()) {
            String path = "aura." + entry.getKey();
            yaml.set(path + ".value", entry.getValue());
            yaml.set(path + ".name", names.getOrDefault(entry.getKey(), "unknown"));
        }
        for (Map.Entry<UUID, LocalDate> entry : lastVote.entrySet()) {
            yaml.set("votes." + entry.getKey(), entry.getValue().toString());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save aura.yml: " + exception.getMessage());
        }
    }

    public record AuraCode(UUID moderator, String moderatorName, long expiresAt) {
    }

    private static final long CODE_TTL_MS = 10 * 60 * 1000L;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final Map<String, AuraCode> codes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public int vote(UUID voter, UUID target, String targetName, int delta) {
        lastVote.put(voter, LocalDate.now());
        return addAura(target, targetName, delta);
    }

    public synchronized int addAura(UUID target, String targetName, int delta) {
        int value = aura.getOrDefault(target, 0) + delta;
        aura.put(target, value);
        names.put(target, targetName);
        save();
        return value;
    }

    public String createCode(UUID moderator, String moderatorName) {
        long now = System.currentTimeMillis();
        codes.values().removeIf(c -> c.expiresAt() < now || c.moderator().equals(moderator));

        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (codes.containsKey(code));

        codes.put(code, new AuraCode(moderator, moderatorName, now + CODE_TTL_MS));
        return code;
    }

    public AuraCode peekCode(String code) {
        AuraCode entry = codes.get(code.toUpperCase());
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt() < System.currentTimeMillis()) {
            codes.remove(code.toUpperCase());
            return null;
        }
        return entry;
    }

    public boolean consumeCode(String code) {
        return codes.remove(code.toUpperCase()) != null;
    }

    public long codeTtlMinutes() {
        return CODE_TTL_MS / 60000L;
    }

    public boolean canVote(UUID voter) {
        return !LocalDate.now().equals(lastVote.get(voter));
    }

    public int getAura(UUID id) {
        return aura.getOrDefault(id, 0);
    }

    public List<AuraEntry> top(int limit) {
        List<AuraEntry> list = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : aura.entrySet()) {
            list.add(new AuraEntry(entry.getKey(), names.getOrDefault(entry.getKey(), "unknown"), entry.getValue()));
        }
        list.sort(Comparator.comparingInt(AuraEntry::value).reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }
}