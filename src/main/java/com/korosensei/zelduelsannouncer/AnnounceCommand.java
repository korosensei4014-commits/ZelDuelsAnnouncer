package com.korosensei.zelduelsannouncer;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.List;

final class AnnounceCommand implements TabExecutor {

    private final ZelDuelsAnnouncer plugin;

    AnnounceCommand(ZelDuelsAnnouncer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>ZelDuelsAnnouncer config reloaded."));
            return true;
        }
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /" + label + " reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
            return List.of("reload");
        }
        return Collections.emptyList();
    }
}
