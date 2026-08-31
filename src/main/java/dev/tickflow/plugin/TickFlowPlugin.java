package dev.tickflow.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class TickFlowPlugin extends JavaPlugin {
    private final TPSCalculator tps = new TPSCalculator();
    private final Set<UUID> bossbarViewers = new HashSet<>();
    private RandomTickController randomTicks;
    private BossBar bossBar;
    private int taskId = -1;
    private boolean shuttingDown;
    private boolean warnedPotionFallback;
    private int bossBarTickCounter;
    private int diagnosticsTickCounter;
    private DiagnosticLogger diagnostics;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        diagnostics = new DiagnosticLogger(getDataFolder());
        tps.setMaxDebt(getConfig().getDouble("max-tick-debt", 80.0D));
        randomTicks = new RandomTickController(
                getConfig().getBoolean("respect-other-random-tick-changes", true)
        );

        org.bukkit.command.PluginCommand command = getCommand("tickflow");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        bossBar = Bukkit.createBossBar("TickFlow", BarColor.GREEN, BarStyle.SOLID);
        if (getConfig().getBoolean("diagnostics-enabled", false)) {
            try {
                diagnostics.start();
            } catch (IllegalStateException exception) {
                getLogger().warning("Could not enable diagnostics: " + exception.getMessage());
            }
        }
        scheduleTickTask();
        logServerVersion();
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        if (randomTicks != null) {
            randomTicks.restore(Bukkit.getWorlds());
        }
        if (diagnostics != null) {
            diagnostics.shutdown();
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
        bossbarViewers.clear();
    }

    private void scheduleTickTask() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::tick, 1L, 1L);
    }

    private void tick() {
        if (shuttingDown) {
            return;
        }

        try {
            tps.tick();
            updateBossBar();

            if (!getConfig().getBoolean("enabled", true)) {
                randomTicks.restore(Bukkit.getWorlds());
                updateDiagnostics();
                return;
            }

            double compensationTps = tps.getCompensationTps();
            if (compensationTps < getConfig().getDouble("minimum-tps-for-compensation", 1.0D)) {
                updateDiagnostics();
                return;
            }

            int missed = tps.claimMissedTicks(getCompensationLimit());
            List<World> worlds = Bukkit.getWorlds();

            if (getConfig().getBoolean("world-time-acceleration", true)) {
                accelerateWorldTime(worlds, missed);
            }

            if (getConfig().getBoolean("random-tickspeed-acceleration", true)) {
                randomTicks.update(worlds, getCappedCompensationTps());
            }

            if (missed <= 0) {
                updateDiagnostics();
                return;
            }

            if (getConfig().getBoolean("pickup-acceleration", false)) {
                acceleratePickup(worlds, missed);
            }
            if (getConfig().getBoolean("mob-timer-acceleration", false)) {
                accelerateMobTimers(worlds, missed);
            }
            if (getConfig().getBoolean("potion-effect-duration-acceleration", false)) {
                acceleratePotionDurations(worlds, missed);
            }
            if (getConfig().getBoolean("tnt-acceleration", false)) {
                accelerateTnt(worlds, missed);
            }

            updateDiagnostics();
        } catch (RuntimeException ex) {
            if (diagnostics != null) {
                diagnostics.event("ERROR", ex.getClass().getName() + ": " + ex.getMessage());
            }
            getLogger().warning(
                    "Tick compensation stopped for this tick: " + ex.getClass().getSimpleName()
            );
        }
    }

    private void updateDiagnostics() {
        if (diagnostics == null || !diagnostics.isEnabled()) {
            return;
        }
        int interval = Math.max(1, getConfig().getInt("diagnostics-sample-interval-ticks", 20));
        if (++diagnosticsTickCounter < interval) {
            return;
        }
        diagnosticsTickCounter = 0;
        diagnostics.sample(
                tps,
                getConfig().getBoolean("enabled", true),
                getConfig().getBoolean("world-time-acceleration", true),
                getConfig().getBoolean("random-tickspeed-acceleration", true),
                getConfig().getBoolean("pickup-acceleration", false),
                getConfig().getBoolean("mob-timer-acceleration", false),
                getConfig().getBoolean("potion-effect-duration-acceleration", false),
                getConfig().getBoolean("tnt-acceleration", false),
                getCompensationLimit(),
                getConfig().getDouble("max-compensation-multiplier", 3.0D)
        );
    }

    private double getCompensationMultiplier() {
        double compensationTps = tps.getCompensationTps();
        if (compensationTps <= 0.0D) {
            return 1.0D;
        }
        double raw = 20.0D / compensationTps;
        double maximum = Math.max(1.0D, getConfig().getDouble("max-compensation-multiplier", 3.0D));
        return Math.min(maximum, raw);
    }

    private double getCappedCompensationTps() {
        double multiplier = getCompensationMultiplier();
        return 20.0D / multiplier;
    }

    private int getCompensationLimit() {
        return Math.max(0, getConfig().getInt("max-compensation-ticks-per-server-tick", 4));
    }

    private void acceleratePickup(List<World> worlds, int missed) {
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item && entity.isValid()) {
                    Item item = (Item) entity;
                    item.setPickupDelay(Math.max(0, item.getPickupDelay() - missed));
                }
            }
        }
    }

    private void accelerateMobTimers(List<World> worlds, int missed) {
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Ageable)) {
                    continue;
                }
                Ageable mob = (Ageable) entity;
                if (!mob.isValid() || mob.getAgeLock()) {
                    continue;
                }
                int age = mob.getAge();
                if (age < 0) {
                    mob.setAge(Math.min(0, age + missed));
                } else if (age > 0) {
                    mob.setAge(Math.max(0, age - missed));
                }
            }
        }
    }

    private void acceleratePotionDurations(List<World> worlds, int missed) {
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof LivingEntity)) {
                    continue;
                }
                LivingEntity living = (LivingEntity) entity;
                if (!living.isValid()) {
                    continue;
                }
                for (PotionEffect effect : living.getActivePotionEffects()) {
                    int duration = effect.getDuration();
                    if (duration <= 0) {
                        continue;
                    }
                    PotionEffect replacement = new PotionEffect(
                            effect.getType(),
                            Math.max(0, duration - missed),
                            effect.getAmplifier(),
                            effect.isAmbient(),
                            effect.hasParticles()
                    );
                    living.addPotionEffect(replacement);
                }
            }
        }

        if (!warnedPotionFallback && getConfig().getBoolean("log-unsupported-features", true)) {
            warnedPotionFallback = true;
            getLogger().warning(
                    "Potion acceleration adjusts duration only; exact extra effect ticks require "
                            + "version-specific internals."
            );
        }
    }

    private void accelerateTnt(List<World> worlds, int missed) {
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TNTPrimed && entity.isValid()) {
                    TNTPrimed tnt = (TNTPrimed) entity;
                    tnt.setFuseTicks(Math.max(0, tnt.getFuseTicks() - missed));
                }
            }
        }
    }

    private void accelerateWorldTime(List<World> worlds, int missed) {
        if (missed <= 0) {
            return;
        }
        for (World world : worlds) {
            world.setFullTime(world.getFullTime() + missed);
        }
    }

    private void updateBossBar() {
        if (bossBar == null || bossbarViewers.isEmpty()) {
            return;
        }

        if (++bossBarTickCounter < 10) {
            return;
        }
        bossBarTickCounter = 0;

        double currentTps = tps.getTps();
        double averageTps = tps.getAverageTps();
        double compensationTps = tps.getCompensationTps();
        double multiplier = getCompensationMultiplier();
        double progress = Math.max(0.0D, Math.min(1.0D, currentTps / 20.0D));
        boolean enabled = getConfig().getBoolean("enabled", true);
        bossBar.setProgress(progress);
        bossBar.setColor(enabled ? tpsColor(currentTps) : BarColor.WHITE);
        bossBar.setTitle(
                ChatColor.GRAY + "TickFlow "
                        + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f TPS", currentTps)
                        + ChatColor.DARK_GRAY + " | "
                        + ChatColor.WHITE + String.format(Locale.ROOT, "%.2fx", multiplier)
                        + ChatColor.DARK_GRAY + " | "
                        + ChatColor.WHITE + String.format(Locale.ROOT, "%.1f avg", averageTps)
                        + ChatColor.DARK_GRAY + " | "
                        + ChatColor.WHITE + String.format(Locale.ROOT, "%.1f debt", tps.getMissedTicks())
                        + ChatColor.DARK_GRAY + " | "
                        + ChatColor.WHITE + (enabled ? "ACTIVE" : "OFF")
        );
    }

    private BarColor tpsColor(double currentTps) {
        if (currentTps >= 18.0D) {
            return BarColor.GREEN;
        }
        if (currentTps >= 10.0D) {
            return BarColor.YELLOW;
        }
        return BarColor.RED;
    }

    private void setBossBarViewer(Player player, boolean enabled) {
        UUID uuid = player.getUniqueId();
        if (enabled) {
            if (bossbarViewers.add(uuid)) {
                bossBar.addPlayer(player);
            }
        } else if (bossbarViewers.remove(uuid)) {
            bossBar.removePlayer(player);
        }
    }

    private boolean hasBossBarViewer(Player player) {
        return bossbarViewers.contains(player.getUniqueId());
    }

    private void logServerVersion() {
        if (!getConfig().getBoolean("log-version-detection", true)) {
            return;
        }
        getLogger().info(
                "Running on " + Bukkit.getServer().getName() + " " + Bukkit.getServer().getBukkitVersion()
        );
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("tickflow.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        String subcommand = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "status":
                sendStatus(sender);
                return true;
            case "tps":
                sender.sendMessage(
                        ChatColor.GRAY + "TPS " + ChatColor.WHITE + format(tps.getTps())
                                + ChatColor.GRAY + " avg " + ChatColor.WHITE + format(tps.getAverageTps())
                                + ChatColor.GRAY + " comp " + ChatColor.WHITE + format(tps.getCompensationTps())
                                + ChatColor.GRAY + " MSPT " + ChatColor.WHITE + format(tps.getMspt())
                                + ChatColor.GRAY + " debt " + ChatColor.WHITE + format(tps.getMissedTicks())
                );
                return true;
            case "toggle":
                boolean enabled = !getConfig().getBoolean("enabled", true);
                getConfig().set("enabled", enabled);
                saveConfig();
                if (!enabled) {
                    randomTicks.restore(Bukkit.getWorlds());
                }
                sender.sendMessage(ChatColor.GRAY + "TickFlow enabled: " + ChatColor.WHITE + enabled);
                return true;
            case "bossbar":
                return handleBossbarCommand(sender, args);
            case "log":
                return handleLogCommand(sender, args);
            case "reload":
                randomTicks.restore(Bukkit.getWorlds());
                reloadConfig();
                randomTicks = new RandomTickController(
                        getConfig().getBoolean("respect-other-random-tick-changes", true)
                );
                tps.setMaxDebt(getConfig().getDouble("max-tick-debt", 80.0D));
                tps.reset();
                warnedPotionFallback = false;
                sender.sendMessage(ChatColor.GREEN + "TickFlow configuration reloaded.");
                return true;
            default:
                sender.sendMessage(
                        ChatColor.GRAY + "Usage: /tickflow <tps|status|toggle|bossbar|log|reload>"
                );
                return true;
        }
    }

    private boolean handleBossbarCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use the bossbar command.");
            return true;
        }

        Player player = (Player) sender;
        boolean show;
        if (args.length >= 2) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("on".equals(action) || "enable".equals(action)) {
                show = true;
            } else if ("off".equals(action) || "disable".equals(action)) {
                show = false;
            } else if ("toggle".equals(action)) {
                show = !hasBossBarViewer(player);
            } else {
                sender.sendMessage(ChatColor.GRAY + "Usage: /tickflow bossbar <on|off|toggle>");
                return true;
            }
        } else {
            show = !hasBossBarViewer(player);
        }

        setBossBarViewer(player, show);
        sender.sendMessage(ChatColor.GRAY + "TickFlow bossbar: " + ChatColor.WHITE + (show ? "ON" : "OFF"));
        return true;
    }

    private boolean handleLogCommand(CommandSender sender, String[] args) {
        if (diagnostics == null) {
            sender.sendMessage(ChatColor.RED + "Diagnostics are not available.");
            return true;
        }

        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "toggle";
        switch (action) {
            case "on":
            case "enable":
                try {
                    diagnostics.start();
                    diagnostics.event("COMMAND", "Diagnostics enabled by " + sender.getName());
                    sender.sendMessage(ChatColor.GREEN + "TickFlow diagnostics enabled.");
                    sender.sendMessage(ChatColor.GRAY + "Log: " + ChatColor.WHITE + diagnostics.getCurrentFile());
                } catch (IllegalStateException exception) {
                    sender.sendMessage(ChatColor.RED + exception.getMessage());
                }
                return true;
            case "off":
            case "disable":
                diagnostics.event("COMMAND", "Diagnostics disabled by " + sender.getName());
                diagnostics.stop();
                sender.sendMessage(ChatColor.GRAY + "TickFlow diagnostics disabled.");
                return true;
            case "status":
                sender.sendMessage(ChatColor.GRAY + "Diagnostics: " + ChatColor.WHITE
                        + (diagnostics.isEnabled() ? "ON" : "OFF"));
                Path log = diagnostics.getCurrentFile();
                if (log != null) {
                    sender.sendMessage(ChatColor.GRAY + "Log: " + ChatColor.WHITE + log);
                }
                return true;
            case "toggle":
                if (diagnostics.isEnabled()) {
                    diagnostics.event("COMMAND", "Diagnostics disabled by " + sender.getName());
                    diagnostics.stop();
                    sender.sendMessage(ChatColor.GRAY + "TickFlow diagnostics: " + ChatColor.WHITE + "OFF");
                    return true;
                }
                try {
                    diagnostics.start();
                    diagnostics.event("COMMAND", "Diagnostics enabled by " + sender.getName());
                    sender.sendMessage(ChatColor.GRAY + "TickFlow diagnostics: " + ChatColor.WHITE + "ON");
                    sender.sendMessage(ChatColor.GRAY + "Log: " + ChatColor.WHITE + diagnostics.getCurrentFile());
                } catch (IllegalStateException exception) {
                    sender.sendMessage(ChatColor.RED + exception.getMessage());
                }
                return true;
            default:
                sender.sendMessage(ChatColor.GRAY + "Usage: /tickflow log <on|off|toggle|status>");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("tickflow.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return matching(args[0], "status", "tps", "toggle", "bossbar", "log", "reload");
        }
        if (args.length == 2 && ("bossbar".equalsIgnoreCase(args[0]) || "log".equalsIgnoreCase(args[0]))) {
            return matching(args[1], "on", "off", "toggle", "status");
        }
        return Collections.emptyList();
    }

    private List<String> matching(String input, String... values) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>(values.length);
        for (String value : values) {
            if (value.startsWith(prefix)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(
                ChatColor.GOLD + "TickFlow " + ChatColor.GRAY + "| enabled="
                        + ChatColor.WHITE + getConfig().getBoolean("enabled", true)
        );
        sender.sendMessage(
                ChatColor.GRAY + "Server: " + ChatColor.WHITE + Bukkit.getServer().getName()
                        + " " + Bukkit.getServer().getBukkitVersion()
        );
        sender.sendMessage(
                ChatColor.GRAY + "TPS: " + ChatColor.WHITE + format(tps.getTps())
                        + ChatColor.GRAY + " avg: " + ChatColor.WHITE + format(tps.getAverageTps())
                        + ChatColor.GRAY + " comp: " + ChatColor.WHITE + format(tps.getCompensationTps())
        );
        sender.sendMessage(
                ChatColor.GRAY + "MSPT: " + ChatColor.WHITE + format(tps.getMspt())
                        + ChatColor.GRAY + " debt: " + ChatColor.WHITE + format(tps.getMissedTicks())
        );
        sender.sendMessage(
                ChatColor.GRAY + "Diagnostics: " + ChatColor.WHITE
                        + (diagnostics != null && diagnostics.isEnabled() ? "ON" : "OFF")
        );
        if (sender instanceof Player) {
            sender.sendMessage(
                    ChatColor.GRAY + "Bossbar: " + ChatColor.WHITE
                            + (hasBossBarViewer((Player) sender) ? "ON" : "OFF")
            );
        }
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
