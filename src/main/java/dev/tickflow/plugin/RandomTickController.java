package dev.tickflow.plugin;

import org.bukkit.GameRules;
import org.bukkit.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Applies bounded random-tick scaling while preserving external gamerule changes. */
final class RandomTickController {
    private static final double NORMAL_TPS = 20.0D;

    private final Map<UUID, Integer> baseValues = new HashMap<>();
    private final Map<UUID, Integer> appliedValues = new HashMap<>();
    private final boolean respectExternalChanges;

    RandomTickController(boolean respectExternalChanges) {
        this.respectExternalChanges = respectExternalChanges;
    }

    void update(List<World> worlds, double effectiveTps) {
        double safeTps = Math.max(1.0D, Math.min(NORMAL_TPS, effectiveTps));
        if (safeTps >= 19.95D) {
            restore(worlds);
            return;
        }

        for (World world : worlds) {
            updateWorld(world, safeTps);
        }
    }

    void restore(List<World> worlds) {
        for (World world : worlds) {
            UUID id = world.getUID();
            Integer base = baseValues.get(id);
            Integer applied = appliedValues.get(id);
            Integer current = read(world);
            if (base != null && applied != null && current != null && current.equals(applied)) {
                set(world, base);
            }
        }
        baseValues.clear();
        appliedValues.clear();
    }

    private void updateWorld(World world, double tps) {
        Integer current = read(world);
        if (current == null) {
            return;
        }

        UUID id = world.getUID();
        Integer previousApplied = appliedValues.get(id);
        if (previousApplied != null && !previousApplied.equals(current)) {
            if (respectExternalChanges) {
                baseValues.put(id, current);
            } else {
                Integer base = baseValues.get(id);
                if (base != null) {
                    set(world, base);
                    current = base;
                }
            }
        } else {
            baseValues.putIfAbsent(id, current);
        }

        int base = baseValues.getOrDefault(id, current);
        int scaled = Math.max(0, (int) Math.ceil(base * NORMAL_TPS / tps));
        if (scaled == current) {
            appliedValues.put(id, current);
            return;
        }

        set(world, scaled);
        appliedValues.put(id, scaled);
    }

    private Integer read(World world) {
        return world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
    }

    private void set(World world, int value) {
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, value);
    }
}
