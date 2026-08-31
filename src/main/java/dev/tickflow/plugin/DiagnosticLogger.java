package dev.tickflow.plugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Writes opt-in diagnostic samples without disk I/O on the server tick thread. */
final class DiagnosticLogger {
    private static final String HEADER =
            "record_type,timestamp,server_software,bukkit_version,java_version,tps,average_tps,mspt,"
                    + "compensation_tps,compensation_multiplier,debt_before_claim,debt_added,debt_after_claim,"
                    + "claimed_ticks,total_claimed_ticks,online_players,worlds,enabled,worldgen_safe,world_time,"
                    + "random_ticks,pickup,mob_timers,potion_duration,tnt,max_compensation,max_entity_updates,"
                    + "pickup_updates,mob_timer_updates,potion_updates,tnt_updates,world_time_updates,"
                    + "skipped_entity_updates,event,message";

    private final Path directory;
    private final ExecutorService writer;
    private BufferedWriter output;
    private Path currentFile;
    private boolean enabled;

    DiagnosticLogger(File dataFolder) {
        directory = dataFolder.toPath().resolve("logs");
        writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "TickFlow-Diagnostics");
            thread.setDaemon(true);
            return thread;
        });
    }

    synchronized void start() {
        if (enabled) {
            return;
        }

        try {
            Files.createDirectories(directory);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
            currentFile = directory.resolve("tickflow-" + timestamp + ".csv");
            output = Files.newBufferedWriter(
                    currentFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            enabled = true;
            writeHeader();
            writeEvent("START", "Diagnostics enabled");
        } catch (IOException exception) {
            enabled = false;
            closeOutput();
            throw new IllegalStateException("Could not create diagnostic log", exception);
        }
    }

    synchronized void stop() {
        if (!enabled) {
            return;
        }
        enabled = false;
        Future<?> closeTask = writer.submit(() -> {
            append(buildEventLine("STOP", "Diagnostics disabled"), true);
            closeOutput();
        });
        await(closeTask);
    }

    synchronized boolean isEnabled() {
        return enabled;
    }

    synchronized Path getCurrentFile() {
        return currentFile;
    }

    void sample(TickTimingSnapshot timing, TickFlowState state, TickFlowConfig config, CompensationStats stats) {
        if (!isEnabled()) {
            return;
        }

        String line = buildSampleLine(timing, state, config, stats);
        writer.execute(() -> append(line, false));
    }

    void event(String event, String message) {
        if (!isEnabled()) {
            return;
        }
        writer.execute(() -> append(buildEventLine(event, message), true));
    }

    synchronized void shutdown() {
        stop();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(2, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
        closeOutput();
    }

    private String buildSampleLine(
            TickTimingSnapshot timing,
            TickFlowState state,
            TickFlowConfig config,
            CompensationStats stats
    ) {
        return "SAMPLE," + csv(timestamp()) + ","
                + csv(Bukkit.getServer().getName()) + ","
                + csv(Bukkit.getServer().getBukkitVersion()) + ","
                + csv(System.getProperty("java.version")) + ","
                + number(timing.tps()) + ","
                + number(timing.averageTps()) + ","
                + number(timing.mspt()) + ","
                + number(timing.compensationTps()) + ","
                + number(timing.compensationMultiplier()) + ","
                + number(timing.debtBeforeClaim()) + ","
                + number(timing.debtAdded()) + ","
                + number(timing.debtAfterClaim()) + ","
                + timing.claimedTicks() + ","
                + timing.totalClaimedTicks() + ","
                + Bukkit.getOnlinePlayers().size() + ","
                + Bukkit.getWorlds().size() + ","
                + state.enabled() + ","
                + state.worldgenSafe() + ","
                + config.worldTimeAcceleration() + ","
                + config.randomTickAcceleration() + ","
                + config.pickupAcceleration() + ","
                + config.mobTimerAcceleration() + ","
                + config.potionDurationAcceleration() + ","
                + config.tntAcceleration() + ","
                + config.maxCompensationTicks() + ","
                + config.maxEntityUpdatesPerTick() + ","
                + stats.pickupUpdates() + ","
                + stats.mobTimerUpdates() + ","
                + stats.potionUpdates() + ","
                + stats.tntUpdates() + ","
                + stats.worldTimeUpdates() + ","
                + stats.skippedEntityUpdates();
    }

    private String buildEventLine(String event, String message) {
        StringBuilder line = new StringBuilder("EVENT,").append(csv(timestamp()));
        for (int index = 2; index < 33; index++) {
            line.append(',');
        }
        line.append(csv(event)).append(',').append(csv(message));
        return line.toString();
    }

    private synchronized void writeHeader() throws IOException {
        output.write(HEADER);
        output.newLine();
        output.write("# server=" + csv(Bukkit.getServer().getName()));
        output.newLine();
        output.write("# bukkitVersion=" + csv(Bukkit.getServer().getBukkitVersion()));
        output.newLine();
        output.write("# java=" + csv(System.getProperty("java.version")));
        output.newLine();
        output.write("# plugins=" + csv(pluginNames()));
        output.newLine();
        output.flush();
    }

    private String pluginNames() {
        StringBuilder names = new StringBuilder();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (names.length() > 0) {
                names.append('|');
            }
            names.append(plugin.getName());
        }
        return names.toString();
    }

    private synchronized void append(String line, boolean flush) {
        if (output == null) {
            return;
        }
        try {
            output.write(line);
            output.newLine();
            if (flush) {
                output.flush();
            }
        } catch (IOException exception) {
            Bukkit.getLogger().warning("TickFlow diagnostics write failed: " + exception.getMessage());
        }
    }

    private void await(Future<?> task) {
        try {
            task.get(2, TimeUnit.SECONDS);
        } catch (Exception exception) {
            task.cancel(true);
            Bukkit.getLogger().warning("TickFlow diagnostics close timed out: " + exception.getMessage());
        }
    }

    private synchronized void writeEvent(String event, String message) {
        append(buildEventLine(event, message), true);
    }

    private synchronized void closeOutput() {
        if (output == null) {
            return;
        }
        try {
            output.flush();
            output.close();
        } catch (IOException exception) {
            Bukkit.getLogger().warning("TickFlow diagnostics close failed: " + exception.getMessage());
        } finally {
            output = null;
        }
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).format(new Date());
    }

    private String number(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String csv(Object value) {
        String text = String.valueOf(value).replace("\"", "\"\"");
        return '"' + text + '"';
    }
}
