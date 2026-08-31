# TickFlow

[![Build](https://github.com/DarkSpirit006/tickflow/actions/workflows/build.yml/badge.svg)](https://github.com/DarkSpirit006/tickflow/actions/workflows/build.yml)
[![CodeFactor](https://www.codefactor.io/repository/github/DarkSpirit006/tickflow/badge?style=flat-square)](https://www.codefactor.io/repository/github/DarkSpirit006/tickflow)
[![License](https://img.shields.io/github/license/DarkSpirit006/tickflow)](https://github.com/DarkSpirit006/tickflow/blob/main/LICENSE)
[![Release](https://img.shields.io/github/v/release/DarkSpirit006/tickflow?display_name=tag)](https://github.com/DarkSpirit006/tickflow/releases)
[![Downloads](https://img.shields.io/github/downloads/DarkSpirit006/tickflow/total)](https://github.com/DarkSpirit006/tickflow/releases)

TickFlow keeps selected time-based gameplay systems closer to real time when a Minecraft server falls behind 20 TPS. It measures server timing, accumulates fractional tick debt, and applies bounded compensation without changing the server's target tick rate.

## Status

TickFlow is experimental software. The primary development and runtime target is **Purpur 26.2 on Java 25**. TickFlow uses the standard `plugin.yml` loader rather than Paper's experimental Paper-plugin loader, so it stays within the normal Bukkit/Spigot/Paper/Purpur plugin model. The current artifact targets 26.2; older Minecraft releases are not marked supported until they have dedicated compatibility validation.

## What TickFlow changes

- Tracks instantaneous and rolling TPS/MSPT.
- Accumulates fractional missed-tick debt instead of rounding every tick.
- Claims whole catch-up ticks once per server tick and shares that claim across discrete features.
- Caps continuous compensation and accumulated debt.
- Protects recent world generation from extra random-tick pressure.
- Keeps optional entity-based compensation disabled by default and limits its work when enabled.
- Provides an opt-in CSV diagnostic logger and a player-facing bossbar.

TickFlow does **not** make the server's main thread execute faster. It compensates gameplay time while the server is behind; it is not a replacement for fixing an overloaded server.

## Commands

```text
/tickflow status
/tickflow tps
/tickflow toggle
/tickflow bossbar <on|off|toggle>
/tickflow log <on|off|toggle|status>
/tickflow reload
```

The default permission is `tickflow.admin` and is granted to operators.

## Configuration

The defaults are intentionally conservative:

```yaml
enabled: true
minimum-tps-for-compensation: 1.0
max-compensation-ticks-per-server-tick: 4
max-compensation-multiplier: 3.0
max-tick-debt: 80.0
max-entity-updates-per-tick: 2000
protect-world-generation: true
worldgen-protection-tps: 12.0
worldgen-protection-exit-tps: 14.0
worldgen-protection-window-ms: 3000
```

Resource-intensive features such as potion, mob, pickup, and TNT compensation are disabled by default.

## Diagnostics

Diagnostics are opt-in:

```text
/tickflow log on
```

CSV files are written to `plugins/TickFlow/logs/`. Samples include TPS, MSPT, rolling TPS, timing debt before/after a claim, claimed ticks, feature activity, world count, player count, and server metadata. The logger does not record chat, IP addresses, or player coordinates.

When reporting a problem, include the diagnostic CSV and the matching server log section.

## Building

TickFlow uses Gradle and requires JDK 25 for the Purpur 26.2 development target.

```bash
./gradlew clean build
```

The JAR is written to `build/libs/`.

## Project structure

```text
src/main/java/dev/tickflow/plugin/
├── BossBarController.java
├── CompensationStats.java
├── DiagnosticLogger.java
├── EntityCompensator.java
├── RandomTickController.java
├── TickFlowCommand.java
├── TickFlowConfig.java
├── TickFlowPlugin.java
├── TickFlowState.java
├── TickTimingSnapshot.java
├── TPSCalculator.java
├── WorldGenerationMonitor.java
└── WorldTimeCompensator.java
```

## Design notes

TickFlow deliberately separates timing, configuration, presentation, diagnostics, and compensation. A failure in one optional feature is isolated so it does not stop the other compensation systems for the entire tick.

The public-API build does not claim exact parity with an NMS/Mixin implementation. Internal Minecraft timer hooks are version-sensitive and must be implemented behind explicit version adapters before a version is marked fully supported.

## Repository

GitHub: https://github.com/DarkSpirit006/tickflow

## License

MIT. See [LICENSE](LICENSE).

TickFlow is an independent project and is not affiliated with Mojang, Microsoft, PaperMC, or Purpur.
