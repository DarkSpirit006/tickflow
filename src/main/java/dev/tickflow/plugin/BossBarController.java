package dev.tickflow.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Owns the optional player-facing TickFlow status bar. */
final class BossBarController {
    private final Set<UUID> viewers = new HashSet<>();
    private BossBar bar;
    private int updateCounter;

    void start() {
        bar = Bukkit.createBossBar("TickFlow", BarColor.GREEN, BarStyle.SOLID);
    }

    void shutdown() {
        if (bar != null) {
            bar.removeAll();
        }
        viewers.clear();
    }

    void update(TickTimingSnapshot timing, TickFlowState state) {
        if (bar == null || viewers.isEmpty()) {
            return;
        }
        if (++updateCounter < 10) {
            return;
        }
        updateCounter = 0;

        double progress = clamp(timing.tps() / 20.0D);
        bar.setProgress(progress);
        bar.setColor(state.enabled() ? colorFor(timing.tps()) : BarColor.WHITE);
        bar.setTitle(formatTitle(timing, state));
    }

    void setViewer(Player player, boolean enabled) {
        if (bar == null) {
            return;
        }

        UUID id = player.getUniqueId();
        if (enabled) {
            if (viewers.add(id)) {
                bar.addPlayer(player);
            }
            return;
        }

        if (viewers.remove(id)) {
            bar.removePlayer(player);
        }
    }

    boolean hasViewer(Player player) {
        return viewers.contains(player.getUniqueId());
    }

    private String formatTitle(TickTimingSnapshot timing, TickFlowState state) {
        String mode = !state.enabled() ? "OFF" : state.worldgenSafe() ? "WORLDGEN SAFE" : "ACTIVE";
        return ChatColor.GRAY + "TickFlow "
                + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f TPS", timing.tps())
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.WHITE + String.format(Locale.ROOT, "%.2fx", timing.compensationMultiplier())
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.WHITE + String.format(Locale.ROOT, "%.1f avg", timing.averageTps())
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f debt", timing.debtAfterClaim())
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.WHITE + timing.claimedTicks() + " catch-up"
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.WHITE + mode;
    }

    private BarColor colorFor(double tps) {
        if (tps >= 18.0D) {
            return BarColor.GREEN;
        }
        if (tps >= 10.0D) {
            return BarColor.YELLOW;
        }
        return BarColor.RED;
    }

    private double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
