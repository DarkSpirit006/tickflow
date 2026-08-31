# TickFlow

TickFlow compensates for lost server ticks so selected time-based gameplay systems stay closer to real time when TPS drops.

## Diagnostics

TickFlow includes an opt-in diagnostic logger intended for troubleshooting. It is off by default and writes low-frequency CSV samples without doing disk I/O on the server tick thread.

Enable a session with:

`/tickflow log on`

Stop it with:

`/tickflow log off`

Check the current session with:

`/tickflow log status`

Or use:

`/tickflow log toggle`

Files are written to `plugins/TickFlow/logs/` using names such as `tickflow-2026-08-31_18-30-00.csv`.

The log contains a header with the server software, Bukkit version, Java version, loaded plugin names, and then timestamped samples containing current TPS, average TPS, MSPT, compensation TPS, compensation multiplier, accumulated tick debt, cumulative compensated ticks, online player count, world count, enabled compensation features, and the compensation cap.

It does not record chat, player coordinates, IP addresses, or command contents.

For troubleshooting, reproduce the problem while diagnostics are enabled and send the CSV together with the matching section of the server console log.

### Timing model
TickFlow keeps fractional timing debt between server ticks. Each completed server tick adds the measured overrun to the debt, then one shared whole-tick claim is made for all discrete compensation features. The continuous compensation multiplier is capped by `max-compensation-multiplier`.
