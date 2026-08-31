package dev.tickflow.plugin;

import org.bukkit.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Advances normal-world time by the number of missed simulation ticks. */
final class WorldTimeCompensator {
    private final Set<UUID> unavailableWorlds = new HashSet<>();

    void compensate(List<World> worlds, int missed, CompensationStats stats, DiagnosticLogger diagnostics) {
        if (missed <= 0) {
            return;
        }

        for (World world : worlds) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            try {
                world.setFullTime(world.getFullTime() + missed);
                stats.addWorldTimeUpdates(1);
            } catch (IllegalArgumentException exception) {
                warnUnavailable(world, exception, diagnostics);
            }
        }
    }

    private void warnUnavailable(World world, IllegalArgumentException exception, DiagnosticLogger diagnostics) {
        if (!unavailableWorlds.add(world.getUID())) {
            return;
        }

        String message = "World-time compensation is unavailable in " + world.getName()
                + ": " + exception.getMessage();
        diagnostics.event("FEATURE_ERROR", message);
    }
}
