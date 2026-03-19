package com.openmemind.ai.memory.core.extraction.insight.buffer;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Insight Build Buffer
 *
 * <p>Buffer items to be processed by InsightType, supporting grouping tags and build status tracking.
 *
 */
public interface InsightBufferStore {

    /**
     * Batch context in the ungrouped stage, returning ungrouped entries and existing group names in one scan.
     *
     * @param ungroupedEntries   entries where groupName == null and built == false
     * @param existingGroupNames all group names that already exist in the buffer (including built)
     */
    record UngroupedContext(List<BufferEntry> ungroupedEntries, Set<String> existingGroupNames) {}

    void append(MemoryId memoryId, String insightTypeName, List<Long> itemIds);

    /**
     * Get ungrouped and unbuilt entries
     *
     * <p>Only returns entries where groupName == null and built == false.
     */
    List<BufferEntry> getUnGrouped(MemoryId memoryId, String insightTypeName);

    /**
     * Count the number of ungrouped and unbuilt entries
     *
     * <p>Only counts entries where groupName == null and built == false.
     */
    int countUnGrouped(MemoryId memoryId, String insightTypeName);

    List<BufferEntry> getGroupUnbuilt(MemoryId memoryId, String insightTypeName, String groupName);

    int countGroupUnbuilt(MemoryId memoryId, String insightTypeName, String groupName);

    void assignGroup(
            MemoryId memoryId, String insightTypeName, List<Long> itemIds, String groupName);

    void markBuilt(MemoryId memoryId, String insightTypeName, List<Long> itemIds);

    /**
     * Return all group names (including built groups)
     *
     * <p>Used for LLM namespace awareness: Phase 2 grouping requires knowledge of the complete group namespace,
     * so that new items can be assigned to existing groups or new groups can be created.
     */
    Set<String> listGroups(MemoryId memoryId, String insightTypeName);

    /**
     * A single scan returns ungrouped entries and existing group names for use in Phase 2.
     *
     * <p>The default implementation calls {@link #getUnGrouped} + {@link #listGroups}, subclasses can override to complete in a single IO.
     */
    default UngroupedContext getUngroupedContext(MemoryId memoryId, String insightTypeName) {
        return new UngroupedContext(
                getUnGrouped(memoryId, insightTypeName), listGroups(memoryId, insightTypeName));
    }

    /**
     * A single scan returns all unbuilt entries of groups for use in Phase 3.
     *
     * <p>Bucketed by groupName, key is groupName, value is the entries under that group where built==false.
     */
    Map<String, List<BufferEntry>> getUnbuiltByGroup(MemoryId memoryId, String insightTypeName);

    /**
     * Check if the specified insightType has pending work (ungrouped or unbuilt)
     *
     * <p>Lightweight check for short-circuit judgment before flush.
     */
    default boolean hasWork(MemoryId memoryId, String insightTypeName) {
        if (countUnGrouped(memoryId, insightTypeName) > 0) return true;
        for (String group : listGroups(memoryId, insightTypeName)) {
            if (countGroupUnbuilt(memoryId, insightTypeName, group) > 0) return true;
        }
        return false;
    }
}
