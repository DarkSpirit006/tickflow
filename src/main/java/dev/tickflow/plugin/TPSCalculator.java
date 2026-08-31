package dev.tickflow.plugin;

import java.util.ArrayDeque;
import java.util.Deque;

/** Tracks tick timing, rolling TPS and accumulated simulation debt. */
public final class TPSCalculator {
    private static final double MAX_TPS = 20.0D;
    private static final double TARGET_TICK_MILLIS = 50.0D;
    private static final int HISTORY_SIZE = 40;
    private double maxDebt = 80.0D;

    private final Deque<Double> history = new ArrayDeque<>(HISTORY_SIZE);
    private long previousTickNanos;
    private long currentTickNanos;
    private double missedTicks;
    private double lastDebtAdded;
    private double debtBeforeClaim;
    private int lastClaimedTicks;
    private long totalCompensatedTicks;

    public void tick() {
        long now = System.nanoTime();
        if (currentTickNanos == 0L) {
            currentTickNanos = now;
            return;
        }

        previousTickNanos = currentTickNanos;
        currentTickNanos = now;

        double mspt = getMspt();
        double debtAdded = Math.max(0.0D, mspt / TARGET_TICK_MILLIS - 1.0D);
        lastDebtAdded = debtAdded;
        missedTicks = Math.min(maxDebt, missedTicks + debtAdded);
        addSample(mspt);
    }

    public double getMspt() {
        if (previousTickNanos == 0L || currentTickNanos == 0L) {
            return TARGET_TICK_MILLIS;
        }
        return (currentTickNanos - previousTickNanos) / 1_000_000.0D;
    }

    public double getTps() {
        double mspt = getMspt();
        return mspt <= 0.0D ? MAX_TPS : Math.min(MAX_TPS, 1_000.0D / mspt);
    }

    public double getAverageTps() {
        if (history.isEmpty()) {
            return MAX_TPS;
        }

        double total = 0.0D;
        for (double sample : history) {
            total += sample;
        }
        return total / history.size();
    }

    public double getCompensationTps() {
        return Math.min(getTps(), getAverageTps());
    }

    public double getMissedTicks() {
        return missedTicks;
    }

    public double getLastDebtAdded() {
        return lastDebtAdded;
    }

    public double getDebtBeforeClaim() {
        return debtBeforeClaim;
    }

    public int getLastClaimedTicks() {
        return lastClaimedTicks;
    }

    /** Claims whole missed ticks for this server tick. */
    public int claimMissedTicks(int limit) {
        lastClaimedTicks = 0;
        debtBeforeClaim = missedTicks;
        if (limit <= 0) {
            return 0;
        }

        int available = (int) Math.floor(missedTicks);
        int claimed = Math.min(limit, Math.max(0, available));
        if (claimed > 0) {
            missedTicks -= claimed;
            totalCompensatedTicks += claimed;
            lastClaimedTicks = claimed;
        }
        return claimed;
    }

    public long getTotalCompensatedTicks() {
        return totalCompensatedTicks;
    }

    public void setMaxDebt(double maxDebt) {
        this.maxDebt = Math.max(1.0D, maxDebt);
        if (missedTicks > this.maxDebt) {
            missedTicks = this.maxDebt;
        }
    }

    public void reset() {
        history.clear();
        previousTickNanos = 0L;
        currentTickNanos = 0L;
        missedTicks = 0.0D;
        lastDebtAdded = 0.0D;
        debtBeforeClaim = 0.0D;
        lastClaimedTicks = 0;
        totalCompensatedTicks = 0L;
    }

    private void addSample(double mspt) {
        double sampleTps = mspt <= 0.0D ? MAX_TPS : Math.min(MAX_TPS, 1_000.0D / mspt);
        if (history.size() == HISTORY_SIZE) {
            history.removeFirst();
        }
        history.addLast(sampleTps);
    }
}
