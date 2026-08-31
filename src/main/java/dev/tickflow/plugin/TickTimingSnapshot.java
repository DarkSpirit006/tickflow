package dev.tickflow.plugin;

/** Immutable timing data captured from a single compensation cycle. */
public record TickTimingSnapshot(
        double tps,
        double averageTps,
        double mspt,
        double compensationTps,
        double compensationMultiplier,
        double debtBeforeClaim,
        double debtAdded,
        double debtAfterClaim,
        int claimedTicks,
        long totalClaimedTicks
) {
}
