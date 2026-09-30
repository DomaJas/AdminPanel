package org.domaja;

import org.domaja.aura.AuraManager;
import org.domaja.check.CheckListener;
import org.domaja.check.CheckManager;
import org.domaja.command.*;
import org.domaja.command.AuraCommand;
import org.domaja.dialog.AdminDialogs;
import org.domaja.listener.PlayerListener;
import org.domaja.moder.ModerManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.domaja.report.ReportManager;

import java.util.List;

public final class AdminPanel extends JavaPlugin {

    private ModerManager moderManager;
    private AdminDialogs adminDialogs;
    private CheckManager checkManager;
    private AuraManager auraManager;
    private ReportManager reportManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.moderManager = new ModerManager(this);
        this.checkManager = new CheckManager(this, moderManager);
        moderManager.setCheckManager(checkManager);

        this.adminDialogs = new AdminDialogs(this, moderManager);
        this.auraManager = new AuraManager(this);
        this.reportManager = new ReportManager(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this, moderManager, checkManager), this);
        getServer().getPluginManager().registerEvents(new CheckListener(checkManager), this);

        registerCommands();
        getLogger().info("AdminPanel enabled.");
    }

    @Override
    public void onDisable() {
        if (checkManager != null) {
            checkManager.endAll();
        }
        if (moderManager != null) {
            moderManager.persistActivePlayersToDisk();
        }
        if (auraManager != null) {
            auraManager.save();
        }
    }

    private void registerCommands() {
        PluginCommand moder = getCommand("moder");
        if (moder != null) {
            ModerCommand executor = new ModerCommand(this, moderManager, adminDialogs);
            moder.setExecutor(executor);
            moder.setTabCompleter(executor);
        }

        PluginCommand panel = getCommand("apanel");
        if (panel != null) {
            panel.setExecutor(new AdminPanelCommand(this, adminDialogs));
        }

        CheckCommand check = new CheckCommand(this, moderManager, checkManager);
        for (String name : List.of("proverka", "approve", "decline")) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(check);
                cmd.setTabCompleter(check);
            }
        }

        PluginCommand aura = getCommand("aura");
        if (aura != null) {
            AuraCommand executor = new AuraCommand(this, auraManager);
            aura.setExecutor(executor);
            aura.setTabCompleter(executor);
        }

        PluginCommand auraTop = getCommand("auratop");
        if (auraTop != null) {
            auraTop.setExecutor(new AuraTopCommand(auraManager));
        }

        PluginCommand report = getCommand("report");
        if (report != null) {
            ReportCommand executor = new ReportCommand(this, reportManager);
            report.setExecutor(executor);
            report.setTabCompleter(executor);
        }
    }

    public ModerManager getModerManager() {
        return moderManager;
    }

    public AuraManager getAuraManager() {
        return auraManager;
    }
}
