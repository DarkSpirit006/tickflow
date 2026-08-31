package dev.tickflow.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Handles the public TickFlow administration command. */
final class TickFlowCommand implements TabExecutor {
    private final TickFlowPlugin plugin;

    TickFlowCommand(TickFlowPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tickflow.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        String subcommand = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "status" -> status(sender);
            case "tps" -> tps(sender);
            case "toggle" -> toggle(sender);
            case "bossbar" -> bossbar(sender, args);
            case "log" -> log(sender, args);
            case "reload" -> reload(sender);
            default -> usage(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
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

    private void status(CommandSender sender) {
        TickTimingSnapshot timing = plugin.timing();
        sender.sendMessage(ChatColor.GOLD + "TickFlow" + ChatColor.GRAY + " | enabled="
                + ChatColor.WHITE + plugin.configuration().enabled());
        sender.sendMessage(ChatColor.GRAY + "Server: " + ChatColor.WHITE
                + Bukkit.getServer().getName() + " " + Bukkit.getServer().getBukkitVersion());
        sender.sendMessage(ChatColor.GRAY + "TPS: " + ChatColor.WHITE + format(timing.tps())
                + ChatColor.GRAY + " avg: " + ChatColor.WHITE + format(timing.averageTps())
                + ChatColor.GRAY + " comp: " + ChatColor.WHITE + format(timing.compensationTps()));
        sender.sendMessage(ChatColor.GRAY + "MSPT: " + ChatColor.WHITE + format(timing.mspt())
                + ChatColor.GRAY + " debt: " + ChatColor.WHITE + format(timing.debtAfterClaim())
                + ChatColor.GRAY + " catch-up: " + ChatColor.WHITE + timing.claimedTicks());
        sender.sendMessage(ChatColor.GRAY + "Worldgen: " + ChatColor.WHITE
                + (plugin.isWorldgenSafe() ? "SAFE" : "NORMAL"));
        sender.sendMessage(ChatColor.GRAY + "Diagnostics: " + ChatColor.WHITE
                + (plugin.isDiagnosticsEnabled() ? "ON" : "OFF"));
        if (sender instanceof Player player) {
            sender.sendMessage(ChatColor.GRAY + "Bossbar: " + ChatColor.WHITE
                    + (plugin.hasBossbar(player) ? "ON" : "OFF"));
        }
    }

    private void tps(CommandSender sender) {
        TickTimingSnapshot timing = plugin.timing();
        sender.sendMessage(ChatColor.GRAY + "TPS " + ChatColor.WHITE + format(timing.tps())
                + ChatColor.GRAY + " avg " + ChatColor.WHITE + format(timing.averageTps())
                + ChatColor.GRAY + " MSPT " + ChatColor.WHITE + format(timing.mspt())
                + ChatColor.GRAY + " multiplier " + ChatColor.WHITE + format(timing.compensationMultiplier())
                + ChatColor.GRAY + " debt " + ChatColor.WHITE + format(timing.debtAfterClaim())
                + ChatColor.GRAY + " catch-up " + ChatColor.WHITE + timing.claimedTicks());
    }

    private void toggle(CommandSender sender) {
        plugin.setTickFlowEnabled(!plugin.configuration().enabled());
        sender.sendMessage(ChatColor.GRAY + "TickFlow enabled: " + ChatColor.WHITE + plugin.configuration().enabled());
    }

    private void bossbar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use the bossbar command.");
            return;
        }

        Boolean show = resolveToggle(plugin.hasBossbar(player), args);
        if (show == null) {
            sender.sendMessage(ChatColor.GRAY + "Usage: /tickflow bossbar <on|off|toggle>");
            return;
        }
        plugin.setBossbar(player, show);
        sender.sendMessage(ChatColor.GRAY + "TickFlow bossbar: " + ChatColor.WHITE + (show ? "ON" : "OFF"));
    }

    private void log(CommandSender sender, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "toggle";
        switch (action) {
            case "on", "enable" -> {
                try {
                    plugin.startDiagnostics(sender.getName());
                    sender.sendMessage(ChatColor.GREEN + "TickFlow diagnostics enabled.");
                    sender.sendMessage(ChatColor.GRAY + "Log: " + ChatColor.WHITE + plugin.diagnosticsFile());
                } catch (IllegalStateException exception) {
                    sender.sendMessage(ChatColor.RED + exception.getMessage());
                }
            }
            case "off", "disable" -> {
                plugin.stopDiagnostics(sender.getName());
                sender.sendMessage(ChatColor.GRAY + "TickFlow diagnostics disabled.");
            }
            case "status" -> {
                sender.sendMessage(ChatColor.GRAY + "Diagnostics: " + ChatColor.WHITE
                        + (plugin.isDiagnosticsEnabled() ? "ON" : "OFF"));
                Path file = plugin.diagnosticsFile();
                if (file != null) {
                    sender.sendMessage(ChatColor.GRAY + "Log: " + ChatColor.WHITE + file);
                }
            }
            case "toggle" -> {
                if (plugin.isDiagnosticsEnabled()) {
                    plugin.stopDiagnostics(sender.getName());
                    sender.sendMessage(ChatColor.GRAY + "TickFlow diagnostics: " + ChatColor.WHITE + "OFF");
                } else {
                    try {
                        plugin.startDiagnostics(sender.getName());
                        sender.sendMessage(ChatColor.GREEN + "TickFlow diagnostics: ON");
                        sender.sendMessage(ChatColor.GRAY + "Log: " + ChatColor.WHITE + plugin.diagnosticsFile());
                    } catch (IllegalStateException exception) {
                        sender.sendMessage(ChatColor.RED + exception.getMessage());
                    }
                }
            }
            default -> sender.sendMessage(ChatColor.GRAY + "Usage: /tickflow log <on|off|toggle|status>");
        }
    }

    private void reload(CommandSender sender) {
        plugin.reloadConfiguration();
        sender.sendMessage(ChatColor.GREEN + "TickFlow configuration reloaded.");
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + "Usage: /tickflow <status|tps|toggle|bossbar|log|reload>");
    }

    private Boolean resolveToggle(boolean current, String[] args) {
        if (args.length < 2 || "toggle".equalsIgnoreCase(args[1])) {
            return !current;
        }
        if ("on".equalsIgnoreCase(args[1]) || "enable".equalsIgnoreCase(args[1])) {
            return true;
        }
        if ("off".equalsIgnoreCase(args[1]) || "disable".equalsIgnoreCase(args[1])) {
            return false;
        }
        return null;
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

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
