package dev.tickflow.plugin;

import org.bukkit.GameRules;
import org.bukkit.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RandomTickController {
    private static final double FULL_TPS = 20.0D;

    private final Map<World, Integer> baseValues = new HashMap<>();
    private final Map<World, Integer> appliedValues = new HashMap<>();
    private final boolean respectExternalChanges;

    RandomTickController(boolean respectExternalChanges) {
        this.respectExternalChanges = respectExternalChanges;
    }

    void update(List<World> worlds, double tps) {
        if (tps >= 19.95D) {
            restore(worlds);
            return;
        }

        for (World world : worlds) {
            updateWorld(world, tps);
        }
    }

    void restore(List<World> worlds) {
        for (World world : worlds) {
            Integer base = baseValues.get(world);
            Integer applied = appliedValues.get(world);
            Integer current = read(world);
            if (base != null && applied != null && current != null && current.equals(applied)) {
                world.setGameRule(GameRules.RANDOM_TICK_SPEED, base);
            }
        }
        appliedValues.clear();
        baseValues.clear();
    }

    private void updateWorld(World world, double tps) {
        Integer current = read(world);
        if (current == null) {
            return;
        }

        Integer applied = appliedValues.get(world);
        if (applied != null && !applied.equals(current) && respectExternalChanges) {
            baseValues.put(world, current);
        } else {
            baseValues.putIfAbsent(world, current);
        }

        int base = baseValues.getOrDefault(world, current);
        int scaled = Math.max(0, (int) Math.ceil(base * FULL_TPS / Math.max(1.0D, tps)));
        if (scaled != current) {
            world.setGameRule(GameRules.RANDOM_TICK_SPEED, scaled);
        }
        appliedValues.put(world, scaled);
    }

    private Integer read(World world) {
        return world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
    }
}
