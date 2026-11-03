package com.korosensei.zelduelsannouncer;

import com.zeltuv.zelduels.api.ZelDuels;
import com.zeltuv.zelduels.api.ZelDuelsAPI;
import com.zeltuv.zelduels.api.events.ArenaDeathEvent;
import com.zeltuv.zelduels.api.events.ArenaEndEvent;
import com.zeltuv.zelduels.api.events.ArenaPreStartEvent;
import com.zeltuv.zelduels.api.events.EndReason;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to ZelDuels' API events and broadcasts the result of every duel.
 *
 * ZelDuels' types are reached with {@code var} / {@code Object} on purpose, so this
 * keeps compiling even if a ZelDuels update changes a return type (e.g. Player vs UUID).
 */
final class DuelListener implements Listener {

    /** What we remember about a duel between its start and its end. */
    private record DuelInfo(String kit, String arena, UUID player1, UUID player2) {
    }

    private final ZelDuelsAnnouncer plugin;
    private final Map<UUID, DuelInfo> duelByPlayer = new ConcurrentHashMap<>();
    /** Winner's health at the moment the loser died, before ZelDuels heals/teleports anyone. */
    private final Map<UUID, Double> winnerHealth = new ConcurrentHashMap<>();

    DuelListener(ZelDuelsAnnouncer plugin) {
        this.plugin = plugin;
    }

    void clear() {
        duelByPlayer.clear();
        winnerHealth.clear();
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDuelPreStart(ArenaPreStartEvent event) {
        String kit = null;
        var settings = event.getDuelSettings();
        if (settings != null) {
            kit = settings.getKitName();
        }
        String arena = null;
        var type = event.getArenaType();
        if (type != null) {
            arena = type.name();
        }

        UUID p1 = uuidOf(event.getPlayer1());
        UUID p2 = uuidOf(event.getPlayer2());
        DuelInfo info = new DuelInfo(kit, arena, p1, p2);
        if (p1 != null) {
            duelByPlayer.put(p1, info);
            winnerHealth.remove(p1);
        }
        if (p2 != null) {
            duelByPlayer.put(p2, info);
            winnerHealth.remove(p2);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDuelDeath(ArenaDeathEvent event) {
        UUID deadId = uuidOf(event.getPlayer());
        if (deadId == null) {
            return;
        }
        Player dead = Bukkit.getPlayer(deadId);
        ZelDuelsAPI api = ZelDuels.getApi();
        if (dead == null || api == null) {
            return;
        }
        var arena = api.getDuelManager().getArenaByPlayer(deadId);
        if (arena == null) {
            return;
        }
        Player opponent = arena.getOpposite(dead);
        if (opponent != null) {
            winnerHealth.put(opponent.getUniqueId(), opponent.getHealth());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDuelEnd(ArenaEndEvent event) {
        UUID winner = uuidOf(event.getWinner());
        UUID loser = uuidOf(event.getLoser());
        EndReason reason = event.getReason();

        DuelInfo info = null;
        if (winner != null) {
            info = duelByPlayer.remove(winner);
        }
        if (loser != null) {
            DuelInfo other = duelByPlayer.remove(loser);
            if (info == null) {
                info = other;
            }
        }
        if (info != null) {
            // Timeouts have no winner/loser, so clean up through the stored players too.
            if (info.player1() != null) duelByPlayer.remove(info.player1());
            if (info.player2() != null) duelByPlayer.remove(info.player2());
        }

        Double health = winner == null ? null : winnerHealth.remove(winner);
        if (loser != null) {
            winnerHealth.remove(loser);
        }
        if (health == null && winner != null) {
            Player online = Bukkit.getPlayer(winner);
            if (online != null) {
                health = online.getHealth();
            }
        }

        final DuelInfo finalInfo = info;
        final Double finalHealth = health;
        runOnMainThread(() -> announce(reason, winner, loser, finalInfo, finalHealth));
    }

    // ------------------------------------------------------------- announcing

    private void announce(EndReason reason, UUID winner, UUID loser, DuelInfo info, Double health) {
        FileConfiguration config = plugin.getConfig();

        String path;
        if (reason == EndReason.TIMEOUT || (winner == null && loser == null)) {
            path = "messages.timeout";
        } else if (reason == EndReason.PLAYER_LEFT || reason == EndReason.DISCONNECT) {
            path = "messages.forfeit";
        } else {
            path = "messages.win";
        }

        if (!config.getBoolean(path + ".enabled", true)) {
            return;
        }
        String template = config.getString(path + ".text");
        if (template == null || template.isBlank()) {
            return;
        }
        // A win/forfeit message needs both names; skip if ZelDuels gave us nothing usable.
        if (!path.equals("messages.timeout") && (winner == null || loser == null)) {
            return;
        }

        String noKit = config.getString("no-kit-name", "No Kit");
        String kit = info != null && info.kit() != null && !info.kit().isBlank() ? info.kit() : noKit;
        String arena = info != null && info.arena() != null ? info.arena() : "Unknown";

        UUID player1 = winner != null ? winner : (info != null ? info.player1() : null);
        UUID player2 = loser != null ? loser : (info != null ? info.player2() : null);

        TagResolver placeholders = TagResolver.resolver(
                Placeholder.unparsed("winner", nameOf(winner)),
                Placeholder.unparsed("loser", nameOf(loser)),
                Placeholder.unparsed("player1", nameOf(player1)),
                Placeholder.unparsed("player2", nameOf(player2)),
                Placeholder.unparsed("kit", kit),
                Placeholder.unparsed("arena", arena),
                Placeholder.unparsed("hearts", formatHearts(health)),
                Placeholder.unparsed("health", health == null ? "?" : String.format(Locale.ROOT, "%.1f", health))
        );

        Component message;
        try {
            message = MiniMessage.miniMessage().deserialize(template, placeholders);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid MiniMessage in config at " + path + ".text: " + e.getMessage());
            return;
        }
        Bukkit.getServer().broadcast(message);
    }

    /** 20 health = 10 hearts. Rounded to half hearts, shown as "7" or "3.5". */
    static String formatHearts(Double health) {
        if (health == null) {
            return "?";
        }
        double hearts = Math.round(Math.max(0, health)) / 2.0;
        if (hearts == 0 && health > 0) {
            hearts = 0.5;
        }
        if (hearts == Math.floor(hearts)) {
            return String.valueOf((long) hearts);
        }
        return String.format(Locale.ROOT, "%.1f", hearts);
    }

    // ---------------------------------------------------------------- helpers

    private void runOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static String nameOf(UUID uuid) {
        if (uuid == null) {
            return "Unknown";
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "Unknown";
    }

    /** Accepts whatever ZelDuels hands us for a player (Player, OfflinePlayer, UUID or name). */
    private static UUID uuidOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof OfflinePlayer player) {
            return player.getUniqueId();
        }
        if (value instanceof Entity entity) {
            return entity.getUniqueId();
        }
        if (value instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                Player byName = Bukkit.getPlayerExact(text);
                return byName != null ? byName.getUniqueId() : null;
            }
        }
        return null;
    }
}
