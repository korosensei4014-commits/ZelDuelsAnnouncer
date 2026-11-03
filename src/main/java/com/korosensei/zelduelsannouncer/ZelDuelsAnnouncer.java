package com.korosensei.zelduelsannouncer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZelDuelsAnnouncer extends JavaPlugin {

    private DuelListener duelListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        AnnounceCommand command = new AnnounceCommand(this);
        if (getCommand("zdannouncer") != null) {
            getCommand("zdannouncer").setExecutor(command);
            getCommand("zdannouncer").setTabCompleter(command);
        }

        // ZelDuels is a soft dependency: only touch its classes when it is installed,
        // otherwise this plugin would crash on load.
        if (Bukkit.getPluginManager().getPlugin("ZelDuels") == null) {
            getLogger().warning("ZelDuels is not installed - duel results will not be announced.");
            return;
        }

        duelListener = new DuelListener(this);
        getServer().getPluginManager().registerEvents(duelListener, this);
        getLogger().info("Hooked into ZelDuels - duel results will be announced in chat.");
    }

    @Override
    public void onDisable() {
        if (duelListener != null) {
            duelListener.clear();
        }
    }
}
