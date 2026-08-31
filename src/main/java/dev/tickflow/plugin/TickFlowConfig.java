package dev.tickflow.plugin;

import org.bukkit.configuration.file.FileConfiguration;

/** Immutable runtime configuration used by the tick loop. */
public record TickFlowConfig(
        boolean enabled,
        double minimumTps,
        int maxCompensationTicks,
        double maxMultiplier,
        double maxDebt,
        boolean worldTimeAcceleration,
        boolean randomTickAcceleration,
        boolean pickupAcceleration,
        boolean mobTimerAcceleration,
        boolean potionDurationAcceleration,
        boolean tntAcceleration,
        boolean respectRandomTickChanges,
        boolean protectWorldGeneration,
        double worldgenProtectionTps,
        double worldgenProtectionExitTps,
        long worldgenProtectionWindowMs,
        int maxEntityUpdatesPerTick,
        boolean logUnsupportedFeatures,
        boolean logVersionDetection,
        int diagnosticsSampleIntervalTicks
) {
    static TickFlowConfig from(FileConfiguration config) {
        double protectionEnter = clamp(
                config.getDouble("worldgen-protection-tps", 12.0D), 1.0D, 20.0D);
        double protectionExit = Math.max(
                protectionEnter,
                clamp(config.getDouble("worldgen-protection-exit-tps", 14.0D), 1.0D, 20.0D)
        );

        return new TickFlowConfig(
                config.getBoolean("enabled", true),
                clamp(config.getDouble("minimum-tps-for-compensation", 1.0D), 0.1D, 20.0D),
                Math.min(20, Math.max(0, config.getInt("max-compensation-ticks-per-server-tick", 4))),
                clamp(config.getDouble("max-compensation-multiplier", 3.0D), 1.0D, 10.0D),
                clamp(config.getDouble("max-tick-debt", 80.0D), 1.0D, 1000.0D),
                config.getBoolean("world-time-acceleration", true),
                config.getBoolean("random-tickspeed-acceleration", true),
                config.getBoolean("pickup-acceleration", false),
                config.getBoolean("mob-timer-acceleration", false),
                config.getBoolean("potion-effect-duration-acceleration", false),
                config.getBoolean("tnt-acceleration", false),
                config.getBoolean("respect-other-random-tick-changes", true),
                config.getBoolean("protect-world-generation", true),
                protectionEnter,
                protectionExit,
                Math.max(250L, config.getLong("worldgen-protection-window-ms", 3000L)),
                Math.min(50000, Math.max(1, config.getInt("max-entity-updates-per-tick", 2000))),
                config.getBoolean("log-unsupported-features", true),
                config.getBoolean("log-version-detection", true),
                Math.max(1, config.getInt("diagnostics-sample-interval-ticks", 20))
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
