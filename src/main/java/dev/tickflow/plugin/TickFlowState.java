package dev.tickflow.plugin;

/** Runtime state presented to diagnostics and the status bar. */
record TickFlowState(boolean enabled, boolean worldgenSafe) {
}
