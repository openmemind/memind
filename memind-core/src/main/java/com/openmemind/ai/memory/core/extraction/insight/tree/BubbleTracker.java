package com.openmemind.ai.memory.core.extraction.insight.tree;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delayed Bubble Tracker
 *
 * <p>Tracks the dirtyCount of Branch/Root, triggers re-summarize when the update count of child nodes reaches the threshold.
 */
public class BubbleTracker implements BubbleTrackerStore {

    private final Map<String, AtomicInteger> dirtyCounts = new ConcurrentHashMap<>();

    /** Marks the node as dirty (child nodes have been updated) */
    public void markDirty(String key) {
        dirtyCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /** Checks whether to trigger re-summarize */
    public boolean shouldResummarize(String key, int threshold) {
        return getDirtyCount(key) >= threshold;
    }

    /** Gets the current dirty count */
    public int getDirtyCount(String key) {
        var counter = dirtyCounts.get(key);
        return counter != null ? counter.get() : 0;
    }

    /** Resets the dirty count after re-summarize */
    public void reset(String key) {
        var counter = dirtyCounts.get(key);
        if (counter != null) {
            counter.set(0);
        }
    }
}
