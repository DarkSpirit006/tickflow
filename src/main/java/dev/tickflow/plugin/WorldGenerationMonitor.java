package dev.tickflow.plugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/** Tracks recent new-chunk activity so optional acceleration can yield CPU to generation. */
final class WorldGenerationMonitor implements Listener {
    private long lastNewChunkNanos;
    private int newChunksSinceLastTick;

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            return;
        }
        lastNewChunkNanos = System.nanoTime();
        newChunksSinceLastTick++;
    }

    boolean hasRecentGeneration(long nowNanos, long windowNanos) {
        return lastNewChunkNanos != 0L && nowNanos - lastNewChunkNanos <= windowNanos;
    }

    int takeNewChunksSinceLastTick() {
        int result = newChunksSinceLastTick;
        newChunksSinceLastTick = 0;
        return result;
    }
}
