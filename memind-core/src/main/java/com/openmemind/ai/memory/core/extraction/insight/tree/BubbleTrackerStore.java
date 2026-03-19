package com.openmemind.ai.memory.core.extraction.insight.tree;

/**
 * Delayed Bubble Tracker Interface
 *
 * <p>Tracks the dirtyCount of Branch/Root, triggering re-summarize when the update count of child nodes reaches the threshold.
 * Supports both memory and persistent implementations.
 *
 * <p>key is a string identifier: BRANCH layer uses "{memoryId}::{insightTypeName}", ROOT layer uses the string form of root.id.
 */
public interface BubbleTrackerStore {

    /** Mark node as dirty (child nodes have been updated) */
    void markDirty(String key);

    /** Check if re-summarize should be triggered */
    boolean shouldResummarize(String key, int threshold);

    /** Get current dirty count */
    int getDirtyCount(String key);

    /** Reset dirty count after re-summarize */
    void reset(String key);
}
