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
import java.util.concurrent.Executors;

/** Writes low-frequency diagnostic samples without blocking the server tick thread. */
final class DiagnosticLogger {
    private static final String HEADER =
            "record_type,timestamp,server_software,bukkit_version,java_version,tps,average_tps,mspt,"
                    + "compensation_tps,compensation_multiplier,debt,debt_added,debt_before_claim,claimed_ticks,"
                    + "total_compensated_ticks,online_players,"
                    + "worlds,enabled,world_time,random_ticks,pickup,mob_timers,potion_duration,tnt,"
                    + "max_compensation,event,message";

    private final Path directory;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TickFlow-Diagnostics");
        thread.setDaemon(true);
        return thread;
    });

    private BufferedWriter output;
    private Path currentFile;
    private boolean enabled;

    DiagnosticLogger(File dataFolder) {
        directory = dataFolder.toPath().resolve("logs");
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
        writeEvent("STOP", "Diagnostics disabled");
        enabled = false;
        closeOutput();
    }

    synchronized boolean isEnabled() {
        return enabled;
    }

    synchronized Path getCurrentFile() {
        return currentFile;
    }

    void sample(TPSCalculator tps, boolean tickFlowEnabled, boolean worldTime, boolean randomTicks,
                boolean pickup, boolean mobTimers, boolean potionDuration, boolean tnt,
                int maxCompensation, double maxMultiplier) {
        if (!isEnabled()) {
            return;
        }

        final String line = buildSampleLine(tps, tickFlowEnabled, worldTime, randomTicks, pickup,
                mobTimers, potionDuration, tnt, maxCompensation, maxMultiplier);
        writer.execute(() -> append(line));
    }

    void event(String event, String message) {
        if (!isEnabled()) {
            return;
        }
        writer.execute(() -> append(buildEventLine(event, message)));
    }

    synchronized void shutdown() {
        if (enabled) {
            writeEvent("STOP", "Plugin disabled");
        }
        enabled = false;
        closeOutput();
        writer.shutdownNow();
    }

    private String buildSampleLine(TPSCalculator tps, boolean tickFlowEnabled, boolean worldTime,
                                   boolean randomTicks, boolean pickup, boolean mobTimers,
                                   boolean potionDuration, boolean tnt, int maxCompensation,
                                   double maxMultiplier) {
        double compensationTps = tps.getCompensationTps();
        double multiplier = compensationTps <= 0.0D ? 1.0D
                : Math.min(maxMultiplier, 20.0D / compensationTps);
        return "SAMPLE," + csv(timestamp()) + ","
                + csv(Bukkit.getServer().getName()) + ","
                + csv(Bukkit.getServer().getBukkitVersion()) + ","
                + csv(System.getProperty("java.version")) + ","
                + number(tps.getTps()) + ","
                + number(tps.getAverageTps()) + ","
                + number(tps.getMspt()) + ","
                + number(compensationTps) + ","
                + number(multiplier) + ","
                + number(tps.getMissedTicks()) + ","
                + number(tps.getLastDebtAdded()) + ","
                + number(tps.getDebtBeforeClaim()) + ","
                + tps.getLastClaimedTicks() + ","
                + tps.getTotalCompensatedTicks() + ","
                + Bukkit.getOnlinePlayers().size() + ","
                + Bukkit.getWorlds().size() + ","
                + tickFlowEnabled + ","
                + worldTime + ","
                + randomTicks + ","
                + pickup + ","
                + mobTimers + ","
                + potionDuration + ","
                + tnt + ","
                + maxCompensation;
    }

    private String buildEventLine(String event, String message) {
        StringBuilder line = new StringBuilder("EVENT,").append(csv(timestamp()));
        for (int i = 0; i < 20; i++) {
            line.append(',');
        }
        line.append(csv(event)).append(',').append(csv(message));
        return line.toString();
    }

    private void writeHeader() throws IOException {
        output.write(HEADER);
        output.newLine();
        output.flush();
        writeEnvironment();
    }

    private void writeEnvironment() throws IOException {
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

    private void append(String line) {
        synchronized (this) {
            if (output == null) {
                return;
            }
            try {
                output.write(line);
                output.newLine();
                output.flush();
            } catch (IOException exception) {
                Bukkit.getLogger().warning("TickFlow diagnostics write failed: " + exception.getMessage());
            }
        }
    }

    private synchronized void writeEvent(String event, String message) {
        if (output == null) {
            return;
        }
        append(buildEventLine(event, message));
    }

    private synchronized void closeOutput() {
        if (output == null) {
            return;
        }
        try {
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
