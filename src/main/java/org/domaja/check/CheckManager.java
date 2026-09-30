    package org.domaja.check;

    import net.kyori.adventure.text.Component;
    import net.kyori.adventure.text.format.NamedTextColor;
    import net.kyori.adventure.title.Title;
    import org.bukkit.Bukkit;
    import org.bukkit.Location;
    import org.bukkit.Material;
    import org.bukkit.World;
    import org.bukkit.attribute.Attribute;
    import org.bukkit.attribute.AttributeInstance;
    import org.bukkit.block.Block;
    import org.bukkit.block.data.BlockData;
    import org.bukkit.entity.Player;
    import org.bukkit.util.Vector;
    import org.domaja.AdminPanel;
    import org.domaja.moder.ModerManager;

    import java.time.Duration;
    import java.util.ArrayList;
    import java.util.HashMap;
    import java.util.List;
    import java.util.Locale;
    import java.util.Map;
    import java.util.UUID;
    import java.util.concurrent.ConcurrentHashMap;
    import java.util.regex.Matcher;
    import java.util.regex.Pattern;

    public final class CheckManager {

        private static final Pattern DURATION = Pattern.compile("^(\\d{1,6})([smhdw])$", Pattern.CASE_INSENSITIVE);

        public record Check(UUID moderator, UUID target, String targetName,
                            Location moderatorOrigin, Location targetOrigin,
                            Map<Block, BlockData> originalBlocks,
                            boolean allowFlight, boolean flying,
                            float walkSpeed, float flySpeed, boolean invulnerable,
                            double jumpStrength) {
        }

        private final AdminPanel plugin;
        private final ModerManager moderManager;
        private final Map<UUID, Check> byTarget = new ConcurrentHashMap<>();
        private final Map<UUID, Check> byModerator = new ConcurrentHashMap<>();
        private boolean internalTeleport;

        public CheckManager(AdminPanel plugin, ModerManager moderManager) {
            this.plugin = plugin;
            this.moderManager = moderManager;
        }

        private static String formatDuration(Duration duration) {
            long seconds = duration.getSeconds();

            if (seconds % 86400 == 0) {
                return (seconds / 86400) + "d";
            }
            if (seconds % 3600 == 0) {
                return (seconds / 3600) + "h";
            }
            if (seconds % 60 == 0) {
                return (seconds / 60) + "m";
            }

            return seconds + "s";
        }

        public boolean isFrozen(Player player) {
            return byTarget.containsKey(player.getUniqueId());
        }

        public boolean isInternalTeleport() {
            return internalTeleport;
        }

        public boolean isPlatformBlock(Block block) {
            for (Check check : byTarget.values()) {
                if (check.originalBlocks().containsKey(block)) {
                    return true;
                }
            }
            return false;
        }

        public List<String> targetNamesOf(Player moder) {
            Check check = byModerator.get(moder.getUniqueId());
            return check == null ? List.of() : List.of(check.targetName());
        }

        public void start(Player moder, Player target) {
            if (byModerator.containsKey(moder.getUniqueId())) {
                msg(moder, "Вы уже проводите проверку. Завершите её: /approve или /decline.", NamedTextColor.RED);
                return;
            }
            if (moder.getUniqueId().equals(target.getUniqueId())) {
                msg(moder, "Нельзя проверять самого себя.", NamedTextColor.RED);
                return;
            }
            if (byTarget.containsKey(target.getUniqueId())) {
                msg(moder, "Этот игрок уже на проверке.", NamedTextColor.RED);
                return;
            }
            if (moderManager.isModerator(target)) {
                msg(moder, "Нельзя проверять игрока в режиме модерации.", NamedTextColor.RED);
                return;
            }

            World world = moder.getWorld();
            int height = Math.min(plugin.getConfig().getInt("check.height", 250), world.getMaxHeight() - 10);
            height = Math.max(height, world.getMinHeight() + 1);
            int radius = Math.max(1, Math.min(8, plugin.getConfig().getInt("check.platform-radius", 3)));
            Material material = platformMaterial();

            Location origin = moder.getLocation();
            int cx = origin.getBlockX();
            int cz = origin.getBlockZ();

            Map<Block, BlockData> originals = new HashMap<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = 0; dy <= 3; dy++) {
                        Block block = world.getBlockAt(cx + dx, height + dy, cz + dz);
                        originals.put(block, block.getBlockData());
                    }
                }
            }

            AttributeInstance jumpAttr = target.getAttribute(Attribute.JUMP_STRENGTH);
            double jump = jumpAttr != null ? jumpAttr.getBaseValue() : 0.42;

            Check check = new Check(
                    moder.getUniqueId(), target.getUniqueId(), target.getName(),
                    origin.clone(), target.getLocation().clone(), originals,
                    target.getAllowFlight(), target.isFlying(),
                    target.getWalkSpeed(), target.getFlySpeed(), target.isInvulnerable(),
                    jump
            );
            byModerator.put(check.moderator(), check);
            byTarget.put(check.target(), check);

            for (Map.Entry<Block, BlockData> entry : originals.entrySet()) {
                Block block = entry.getKey();
                if (block.getY() == height) {
                    block.setType(material, false);
                } else if (!block.getType().isAir()) {
                    block.setType(Material.AIR, false);
                }
            }

            Location modLoc = new Location(world, cx + 0.5, height + 1, cz - 0.5, 0f, 0f);
            Location targetLoc = new Location(world, cx + 0.5, height + 1, cz + 1.5, 180f, 0f);

            boolean ok;
            internalTeleport = true;
            try {
                moder.leaveVehicle();
                target.leaveVehicle();
                target.closeInventory();
                ok = moder.teleport(modLoc) && target.teleport(targetLoc);
            } finally {
                internalTeleport = false;
            }

            if (!ok) {
                restore(check, null);
                msg(moder, "Не удалось начать проверку (телепортация отменена).", NamedTextColor.RED);
                return;
            }

            target.setAllowFlight(false);
            target.setFlying(false);
            target.setWalkSpeed(0f);
            target.setFlySpeed(0f);
            target.setInvulnerable(true);
            if (jumpAttr != null) {
                jumpAttr.setBaseValue(0);
            }

            target.showTitle(Title.title(
                    Component.text("Вы на проверке", NamedTextColor.RED),
                    Component.text("Следуйте указаниям модератора"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(500))
            ));
            msg(target, "Вы на проверке, следуйте указаниям модератора", NamedTextColor.RED);
            msg(moder, "Игрок " + target.getName() + " отправлен на проверку. Завершить: /approve " + target.getName()
                    + " или /decline " + target.getName() + " <время> <причина>", NamedTextColor.GREEN);
        }

        public void approve(Player moder, Player target) {
            Check check = requireCheck(moder, target);
            if (check == null) {
                return;
            }
            restore(check, null);
            msg(target, "Проверка пройдена. Вы свободны.", NamedTextColor.GREEN);
            msg(moder, "Игрок " + target.getName() + " отпущен.", NamedTextColor.GREEN);
        }

        public void decline(Player moder, Player target, Duration duration, String reason) {
            Check check = requireCheck(moder, target);
            if (check == null) {
                return;
            }

            restore(check, null);

            String time = formatDuration(duration);

            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    "tempban " + target.getName() + " " + time + " " + reason
            );

            msg(moder,
                    "Игрок " + target.getName() + " заблокирован. Причина: " + reason,
                    NamedTextColor.GREEN
            );
        }

        public void handleQuit(Player player) {
            UUID id = player.getUniqueId();

            Check asTarget = byTarget.get(id);
            if (asTarget != null) {
                Player moder = Bukkit.getPlayer(asTarget.moderator());
                restore(asTarget, player);

                String reason = plugin.getConfig().getString("check.quit-ban-reason", "Выход с сервера во время проверки");
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        "tempban " + player.getName() + " 7d" + " " + reason
                );

                if (moder != null) {
                    msg(moder, "Игрок " + player.getName() + " вышел во время проверки и заблокирован на 7 дней.",
                            NamedTextColor.YELLOW);
                }
            }

            Check asModer = byModerator.get(id);
            if (asModer != null) {
                Player target = Bukkit.getPlayer(asModer.target());
                restore(asModer, player);
                if (target != null) {
                    msg(target, "Проверка отменена. Вы свободны.", NamedTextColor.GREEN);
                }
            }
        }

        public void endByModerator(Player moder) {
            Check check = byModerator.get(moder.getUniqueId());
            if (check == null) {
                return;
            }
            Player target = Bukkit.getPlayer(check.target());
            restore(check, null);
            if (target != null) {
                msg(target, "Проверка отменена. Вы свободны.", NamedTextColor.GREEN);
            }
        }

        public void endAll() {
            for (Check check : new ArrayList<>(byModerator.values())) {
                try {
                    restore(check, null);
                } catch (Exception exception) {
                    plugin.getLogger().warning("Could not end check for " + check.targetName() + ": " + exception.getMessage());
                }
            }
        }

        private Check requireCheck(Player moder, Player target) {
            Check check = target == null ? null : byTarget.get(target.getUniqueId());
            if (check == null || !check.moderator().equals(moder.getUniqueId())) {
                msg(moder, "Этот игрок не находится на проверке у вас.", NamedTextColor.RED);
                return null;
            }
            return check;
        }

        private void restore(Check check, Player hint) {
            byTarget.remove(check.target());
            byModerator.remove(check.moderator());

            Player moder = resolve(check.moderator(), hint);
            Player target = resolve(check.target(), hint);

            internalTeleport = true;
            try {
                if (target != null) {
                    target.setWalkSpeed(check.walkSpeed());
                    target.setFlySpeed(check.flySpeed());
                    target.setInvulnerable(check.invulnerable());
                    target.setAllowFlight(check.allowFlight());
                    target.setFlying(check.flying() && check.allowFlight());
                    teleportBack(target, check.targetOrigin());

                    AttributeInstance attr = target.getAttribute(Attribute.JUMP_STRENGTH);
                    if (attr != null) {
                        attr.setBaseValue(check.jumpStrength());
                    }
                }
                if (moder != null) {
                    teleportBack(moder, check.moderatorOrigin());
                }
            } finally {
                internalTeleport = false;
            }

            for (Map.Entry<Block, BlockData> entry : check.originalBlocks().entrySet()) {
                entry.getKey().setBlockData(entry.getValue(), false);
            }
        }

        private Player resolve(UUID id, Player hint) {
            if (hint != null && hint.getUniqueId().equals(id)) {
                return hint;
            }
            return Bukkit.getPlayer(id);
        }

        private void teleportBack(Player player, Location location) {
            if (location.getWorld() == null) {
                return;
            }
            player.leaveVehicle();
            player.setFallDistance(0f);
            player.setVelocity(new Vector());
            player.teleport(location.clone());
        }

        private Material platformMaterial() {
            try {
                Material material = Material.valueOf(
                        plugin.getConfig().getString("check.platform-material", "GLASS").toUpperCase(Locale.ROOT));
                if (material.isBlock() && !material.isAir()) {
                    return material;
                }
            } catch (IllegalArgumentException ignored) {
            }
            return Material.GLASS;
        }

        private static void msg(Player player, String text, NamedTextColor color) {
            player.sendMessage(Component.text(text, color));
        }

        public static Duration parseDuration(String raw) {
            Matcher matcher = DURATION.matcher(raw.trim());
            if (!matcher.matches()) {
                return null;
            }
            long amount = Long.parseLong(matcher.group(1));
            if (amount <= 0) {
                return null;
            }
            return switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
                case 's' -> Duration.ofSeconds(amount);
                case 'm' -> Duration.ofMinutes(amount);
                case 'h' -> Duration.ofHours(amount);
                case 'd' -> Duration.ofDays(amount);
                default -> Duration.ofDays(amount * 7);
            };
        }
    }