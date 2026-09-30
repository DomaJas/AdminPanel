package org.domaja.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.domaja.aura.AuraManager;

import java.util.List;

public final class AuraTopCommand implements CommandExecutor {

    private final AuraManager auraManager;

    public AuraTopCommand(AuraManager auraManager) {
        this.auraManager = auraManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<AuraManager.AuraEntry> top = auraManager.top(10);
        if (top.isEmpty()) {
            sender.sendMessage("§eПока ни у одного модератора нет ауры.");
            return true;
        }

        sender.sendMessage("§6§lТоп модераторов по ауре:");
        int place = 1;
        for (AuraManager.AuraEntry entry : top) {
            sender.sendMessage("§e" + place + ". §f" + entry.name() + " §7— §a" + entry.value());
            place++;
        }
        return true;
    }
}