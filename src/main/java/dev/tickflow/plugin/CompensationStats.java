package dev.tickflow.plugin;

/** Counts work performed by optional compensation features during one cycle. */
final class CompensationStats {
    private int pickupUpdates;
    private int mobTimerUpdates;
    private int potionUpdates;
    private int tntUpdates;
    private int worldTimeUpdates;
    private int skippedEntityUpdates;

    void addPickupUpdates(int count) {
        pickupUpdates += count;
    }

    void addMobTimerUpdates(int count) {
        mobTimerUpdates += count;
    }

    void addPotionUpdates(int count) {
        potionUpdates += count;
    }

    void addTntUpdates(int count) {
        tntUpdates += count;
    }

    void addWorldTimeUpdates(int count) {
        worldTimeUpdates += count;
    }

    void addSkippedEntityUpdates(int count) {
        skippedEntityUpdates += count;
    }

    int pickupUpdates() {
        return pickupUpdates;
    }

    int mobTimerUpdates() {
        return mobTimerUpdates;
    }

    int potionUpdates() {
        return potionUpdates;
    }

    int tntUpdates() {
        return tntUpdates;
    }

    int worldTimeUpdates() {
        return worldTimeUpdates;
    }

    int skippedEntityUpdates() {
        return skippedEntityUpdates;
    }
}
