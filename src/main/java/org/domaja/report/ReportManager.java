package org.domaja.report;

import org.bukkit.entity.Player;
import org.domaja.AdminPanel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReportManager {

    public record Report(int id, UUID reporter, String reporterName,
                         String target, String reason, long createdAt) {
    }

    private final AdminPanel plugin;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, Report> reports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();

    public ReportManager(AdminPanel plugin) {
        this.plugin = plugin;
    }

    public long cooldownMillis() {
        return plugin.getConfig().getLong("report.cooldown-minutes", 10) * 60_000L;
    }

    public long remainingSeconds(UUID player) {
        Long last = lastReport.get(player);
        if (last == null) {
            return 0;
        }
        long left = last + cooldownMillis() - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    public Report create(Player reporter, String target, String reason) {
        Report report = new Report(nextId.getAndIncrement(), reporter.getUniqueId(),
                reporter.getName(), target, reason, System.currentTimeMillis());
        reports.put(report.id(), report);
        lastReport.put(reporter.getUniqueId(), report.createdAt());
        return report;
    }

    public Report close(int id) {
        return reports.remove(id);
    }

    public List<Report> open() {
        List<Report> list = new ArrayList<>(reports.values());
        list.sort(Comparator.comparingInt(Report::id));
        return list;
    }
}