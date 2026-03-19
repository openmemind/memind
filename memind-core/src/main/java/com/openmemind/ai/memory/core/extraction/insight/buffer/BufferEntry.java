package com.openmemind.ai.memory.core.extraction.insight.buffer;

/**
 * Insight buffer entry
 *
 * @param itemId    item business ID
 * @param groupName group name (null when ungrouped)
 * @param built     whether it has been built as Insight
 */
public record BufferEntry(long itemId, String groupName, boolean built) {

    /** Create an ungrouped, unbuilt entry */
    public static BufferEntry ungrouped(long itemId) {
        return new BufferEntry(itemId, null, false);
    }

    /** Whether it is ungrouped */
    public boolean isUngrouped() {
        return groupName == null;
    }
}
