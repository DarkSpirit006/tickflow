package dev.tickflow.plugin;

import java.util.ArrayDeque;
import java.util.Deque;

/** Measures server tick timing and maintains compensation debt. */
public final class TPSCalculator {
    private static final double TARGET_TPS = 20.0D;
    private static final double TARGET_TICK_MS = 50.0D;
    private static final int HISTORY_SIZE = 40;

    private final Deque<Double> tpsHistory = new ArrayDeque<>(HISTORY_SIZE);

    private long previousTickNanos;
    private long currentTickNanos;
    private double debt;
    private double maxDebt = 80.0D;
    private double lastDebtAdded;
    private double debtBeforeClaim;
    private int lastClaimedTicks;
    private long totalClaimedTicks;

    /** Records one completed server tick. */
    public void tick() {
        long now = System.nanoTime();
        if (currentTickNanos == 0L) {
            currentTickNanos = now;
            return;
        }

        previousTickNanos = currentTickNanos;
        currentTickNanos = now;

        double mspt = getMspt();
        double debtAdded = Math.max(0.0D, mspt / TARGET_TICK_MS - 1.0D);
        lastDebtAdded = debtAdded;
        debt = Math.min(maxDebt, debt + debtAdded);

        addTpsSample(mspt);
    }

    /** Claims whole missed ticks for the current server tick. */
    public int claimMissedTicks(int limit) {
        debtBeforeClaim = debt;
        lastClaimedTicks = 0;

        if (limit <= 0) {
            return 0;
        }

        int available = (int) Math.floor(debt);
        int claimed = Math.min(limit, Math.max(0, available));
        if (claimed == 0) {
            return 0;
        }

        debt -= claimed;
        lastClaimedTicks = claimed;
        totalClaimedTicks += claimed;
        return claimed;
    }

    public double getMspt() {
        if (previousTickNanos == 0L || currentTickNanos == 0L) {
            return TARGET_TICK_MS;
        }
        return (currentTickNanos - previousTickNanos) / 1_000_000.0D;
    }

    public double getTps() {
        return tpsFromMspt(getMspt());
    }

    public double getAverageTps() {
        if (tpsHistory.isEmpty()) {
            return TARGET_TPS;
        }

        double total = 0.0D;
        for (double sample : tpsHistory) {
            total += sample;
        }
        return total / tpsHistory.size();
    }

    public double getCompensationTps() {
        return Math.min(getTps(), getAverageTps());
    }

    public double getDebt() {
        return debt;
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

    public long getTotalClaimedTicks() {
        return totalClaimedTicks;
    }

    public TickTimingSnapshot snapshot(double maxMultiplier) {
        double compensationTps = getCompensationTps();
        double rawMultiplier = compensationTps <= 0.0D ? 1.0D : TARGET_TPS / compensationTps;
        double multiplier = Math.min(Math.max(1.0D, maxMultiplier), rawMultiplier);
        return new TickTimingSnapshot(
                getTps(),
                getAverageTps(),
                getMspt(),
                compensationTps,
                multiplier,
                debtBeforeClaim,
                lastDebtAdded,
                debt,
                lastClaimedTicks,
                totalClaimedTicks
        );
    }

    public void setMaxDebt(double maxDebt) {
        this.maxDebt = Math.max(1.0D, maxDebt);
        debt = Math.min(debt, this.maxDebt);
    }

    /** Clears accumulated compensation debt without discarding timing history. */
    public void clearDebt() {
        debt = 0.0D;
        lastDebtAdded = 0.0D;
        debtBeforeClaim = 0.0D;
        lastClaimedTicks = 0;
    }

    public void reset() {
        tpsHistory.clear();
        previousTickNanos = 0L;
        currentTickNanos = 0L;
        debt = 0.0D;
        lastDebtAdded = 0.0D;
        debtBeforeClaim = 0.0D;
        lastClaimedTicks = 0;
        totalClaimedTicks = 0L;
    }

    private void addTpsSample(double mspt) {
        double sample = tpsFromMspt(mspt);
        if (tpsHistory.size() == HISTORY_SIZE) {
            tpsHistory.removeFirst();
        }
        tpsHistory.addLast(sample);
    }

    private double tpsFromMspt(double mspt) {
        if (mspt <= 0.0D) {
            return TARGET_TPS;
        }
        return Math.min(TARGET_TPS, 1000.0D / mspt);
    }
}
