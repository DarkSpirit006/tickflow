package dev.tickflow.plugin;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Main TickFlow plugin lifecycle and server-tick coordinator. */
public final class TickFlowPlugin extends JavaPlugin {
    private final TPSCalculator tpsCalculator = new TPSCalculator();
    private final WorldGenerationMonitor worldGenerationMonitor = new WorldGenerationMonitor();
    private final WorldTimeCompensator worldTimeCompensator = new WorldTimeCompensator();
    private final EntityCompensator entityCompensator = new EntityCompensator();
    private BossBarController bossBarController;
    private DiagnosticLogger diagnostics;
    private RandomTickController randomTickController;
    private TickFlowConfig configuration;
    private int tickTaskId = -1;
    private int diagnosticsTickCounter;
    private boolean shuttingDown;
    private boolean worldgenSafe;
    private TickTimingSnapshot lastTiming;
    private final Map<String, Long> featureWarningTimes = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configuration = TickFlowConfig.from(getConfig());
        tpsCalculator.setMaxDebt(configuration.maxDebt());

        diagnostics = new DiagnosticLogger(getDataFolder());
        randomTickController = new RandomTickController(configuration.respectRandomTickChanges());
        bossBarController = new BossBarController();
        bossBarController.start();

        Bukkit.getPluginManager().registerEvents(worldGenerationMonitor, this);
        registerCommand();
        scheduleTickTask();

        if (configuration.logVersionDetection()) {
            logEnvironment();
        }
        if (configuration.logUnsupportedFeatures()) {
            logFeatureLimits();
        }
        if (configuration.diagnosticsSampleIntervalTicks() > 0
                && getConfig().getBoolean("diagnostics-enabled", false)) {
            try {
                diagnostics.start();
            } catch (IllegalStateException exception) {
                getLogger().warning(exception.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        if (tickTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tickTaskId);
        }
        restoreRandomTicks();
        if (diagnostics != null) {
            diagnostics.shutdown();
        }
        if (bossBarController != null) {
            bossBarController.shutdown();
        }
    }

    TickTimingSnapshot timing() {
        return lastTiming != null
                ? lastTiming
                : tpsCalculator.snapshot(configuration == null ? 3.0D : configuration.maxMultiplier());
    }

    TickFlowConfig configuration() {
        return configuration;
    }

    boolean isWorldgenSafe() {
        return worldgenSafe;
    }

    boolean isDiagnosticsEnabled() {
        return diagnostics != null && diagnostics.isEnabled();
    }

    Path diagnosticsFile() {
        return diagnostics == null ? null : diagnostics.getCurrentFile();
    }

    boolean hasBossbar(Player player) {
        return bossBarController != null && bossBarController.hasViewer(player);
    }

    void setBossbar(Player player, boolean enabled) {
        if (bossBarController != null) {
            bossBarController.setViewer(player, enabled);
        }
    }

    void setTickFlowEnabled(boolean enabled) {
        if (configuration.enabled() == enabled) {
            return;
        }
        getConfig().set("enabled", enabled);
        saveConfig();
        reloadConfiguration();
    }

    void startDiagnostics(String actor) {
        diagnostics.start();
        diagnostics.event("COMMAND", "Diagnostics enabled by " + actor);
    }

    void stopDiagnostics(String actor) {
        if (!diagnostics.isEnabled()) {
            return;
        }
        diagnostics.event("COMMAND", "Diagnostics disabled by " + actor);
        diagnostics.stop();
    }

    void reloadConfiguration() {
        restoreRandomTicks();
        reloadConfig();
        configuration = TickFlowConfig.from(getConfig());
        tpsCalculator.setMaxDebt(configuration.maxDebt());
        tpsCalculator.clearDebt();
        randomTickController = new RandomTickController(configuration.respectRandomTickChanges());
        worldgenSafe = false;
        diagnosticsTickCounter = 0;
        featureWarningTimes.clear();
    }

    private void registerCommand() {
        PluginCommand command = getCommand("tickflow");
        if (command == null) {
            throw new IllegalStateException("TickFlow command is missing from plugin.yml");
        }
        TickFlowCommand executor = new TickFlowCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void scheduleTickTask() {
        tickTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::tick, 1L, 1L);
    }

    private void tick() {
        if (shuttingDown) {
            return;
        }

        tpsCalculator.tick();
        TickFlowConfig config = configuration;
        boolean enabled = config.enabled();
        CompensationCycle result = compensate(config, enabled);
        lastTiming = tpsCalculator.snapshot(config.maxMultiplier());
        worldgenSafe = result.worldgenSafe;

        if (bossBarController != null) {
            bossBarController.update(lastTiming, new TickFlowState(enabled, worldgenSafe));
        }
        updateDiagnostics(config, result.stats);
    }

    private CompensationCycle compensate(TickFlowConfig config, boolean enabled) {
        CompensationStats stats = new CompensationStats();
        if (!enabled) {
            restoreRandomTicks();
            return new CompensationCycle(false, stats);
        }

        double compensationTps = tpsCalculator.getCompensationTps();
        if (compensationTps < config.minimumTps()) {
            restoreRandomTicks();
            return new CompensationCycle(false, stats);
        }

        boolean safe = shouldProtectWorldGeneration(config);
        int missed = tpsCalculator.claimMissedTicks(config.maxCompensationTicks());
        List<World> worlds = Bukkit.getWorlds();

        if (config.worldTimeAcceleration()) {
            runSafely("world time", () -> worldTimeCompensator.compensate(
                    worlds, missed, stats, diagnostics));
        }

        if (config.randomTickAcceleration() && !safe) {
            runSafely("random ticks", () -> randomTickController.update(
                    worlds, effectiveRandomTickTps(config)));
        } else {
            restoreRandomTicks();
        }

        if (missed <= 0) {
            return new CompensationCycle(safe, stats);
        }

        int limit = config.maxEntityUpdatesPerTick();
        if (config.pickupAcceleration()) {
            runSafely("pickup acceleration", () ->
                    entityCompensator.acceleratePickup(worlds, missed, limit, stats));
        }
        if (config.mobTimerAcceleration()) {
            runSafely("mob timer acceleration", () ->
                    entityCompensator.accelerateMobTimers(worlds, missed, limit, stats));
        }
        if (config.potionDurationAcceleration()) {
            runSafely("potion duration acceleration", () ->
                    entityCompensator.acceleratePotionDurations(worlds, missed, limit, stats));
        }
        if (config.tntAcceleration()) {
            runSafely("TNT acceleration", () ->
                    entityCompensator.accelerateTnt(worlds, missed, limit, stats));
        }
        return new CompensationCycle(safe, stats);
    }

    private double effectiveRandomTickTps(TickFlowConfig config) {
        return Math.max(20.0D / config.maxMultiplier(), tpsCalculator.getCompensationTps());
    }

    private boolean shouldProtectWorldGeneration(TickFlowConfig config) {
        if (!config.protectWorldGeneration()) {
            return false;
        }

        long now = System.nanoTime();
        long window = config.worldgenProtectionWindowMs() * 1_000_000L;
        boolean recentGeneration = worldGenerationMonitor.hasRecentGeneration(now, window);
        if (recentGeneration) {
            return true;
        }

        if (worldgenSafe) {
            return tpsCalculator.getTps() < config.worldgenProtectionExitTps();
        }
        return tpsCalculator.getTps() < config.worldgenProtectionTps();
    }

    private void updateDiagnostics(TickFlowConfig config, CompensationStats stats) {
        if (diagnostics == null || !diagnostics.isEnabled()) {
            return;
        }
        if (++diagnosticsTickCounter < config.diagnosticsSampleIntervalTicks()) {
            return;
        }
        diagnosticsTickCounter = 0;
        diagnostics.sample(lastTiming, new TickFlowState(config.enabled(), worldgenSafe), config, stats);
    }

    private void runSafely(String feature, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            String message = "" + exception.getClass().getSimpleName() + ": " + exception.getMessage();
            if (diagnostics != null) {
                diagnostics.event("FEATURE_ERROR", feature + " failed - " + message);
            }
            warnFeature(feature, message);
        }
    }

    private void warnFeature(String feature, String message) {
        long now = System.nanoTime();
        long last = featureWarningTimes.getOrDefault(feature, 0L);
        if (now - last < 30_000_000_000L) {
            return;
        }
        featureWarningTimes.put(feature, now);
        getLogger().warning("TickFlow " + feature + " skipped: " + message);
    }

    private void restoreRandomTicks() {
        if (randomTickController != null) {
            randomTickController.restore(Bukkit.getWorlds());
        }
    }

    private void logEnvironment() {
        getLogger().info("Running on " + Bukkit.getServer().getName()
                + " " + Bukkit.getServer().getBukkitVersion());
        getLogger().info("Minecraft " + Bukkit.getServer().getMinecraftVersion()
                + " | Java " + System.getProperty("java.version"));
    }

    private void logFeatureLimits() {
        getLogger().info("Optional entity compensation is disabled by default and bounded by configuration.");
        getLogger().info("Exact internal timer hooks are not enabled by this API-only build.");
    }

    private static final class CompensationCycle {
        private final boolean worldgenSafe;
        private final CompensationStats stats;

        private CompensationCycle(boolean worldgenSafe, CompensationStats stats) {
            this.worldgenSafe = worldgenSafe;
            this.stats = stats;
        }
    }
}
