package com.openmemind.ai.memory.core.store;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Memory data persistence storage interface
 *
 */
public interface MemoryStore {

    // ===== MemoryRawData =====

    /**
     * Save raw data
     */
    void saveRawData(MemoryId id, MemoryRawData rawData);

    /**
     * Batch save raw data
     */
    void saveRawDataList(MemoryId id, List<MemoryRawData> rawDataList);

    /**
     * Get raw data by ID
     */
    Optional<MemoryRawData> getRawData(MemoryId id, String rawDataId);

    /**
     * Find raw data by content hash (for idempotent check)
     */
    Optional<MemoryRawData> getRawDataByContentId(MemoryId id, String contentId);

    /**
     * Get all raw data
     */
    List<MemoryRawData> getAllRawData(MemoryId id);

    /**
     * Delete raw data
     */
    void deleteRawData(MemoryId id, String rawDataId);

    /**
     * Poll for raw data that has not been vectorized under the specified memory.
     *
     * <p>Implementation needs to ensure:
     * <ul>
     *   <li>Only return records that currently do not have {@code captionVectorId};
     *   <li>Only return records created earlier than the current time minus {@code minAge}, to avoid competition with the write path;
     *   <li>The same record should not be returned repeatedly by different worker threads before being updated by a worker thread (can be achieved through optimistic locking or "claim" marking).
     * </ul>
     *
     * @param id Memory identifier
     * @param limit Maximum number of records to return
     * @param minAge Minimum age, only records earlier than this time window will be returned
     * @return List of raw data to be vectorized
     */
    List<MemoryRawData> pollRawDataWithoutVector(MemoryId id, int limit, Duration minAge);

    /**
     * Batch update the vector ID and additional metadata of raw data.
     *
     * <p>Implementation should use the keys in {@code vectorIds} as business IDs (i.e. {@link MemoryRawData#id()}),
     * and update the {@code captionVectorId} field and metadata of the corresponding records. Typically used for asynchronous vectorization worker threads to
     * fill back the vector ID after computation is complete, and increase retry count, recent attempt time, and other information.
     *
     * @param id Memory identifier
     * @param vectorIds Mapping from business ID to vector ID
     * @param metadataPatch Additional metadata patch, which will be merged with existing metadata
     */
    void updateRawDataVectorIds(
            MemoryId id, Map<String, String> vectorIds, Map<String, Object> metadataPatch);

    // ===== MemoryItem =====

    /**
     * Add memory item
     */
    void addItem(MemoryId id, MemoryItem item);

    /**
     * Batch add memory items
     */
    void addItems(MemoryId id, List<MemoryItem> items);

    /**
     * Get memory item by ID
     */
    Optional<MemoryItem> getItem(MemoryId id, Long itemId);

    /**
     * Find memory item by content hash (for deduplication)
     */
    Optional<MemoryItem> getItemByContentHash(MemoryId id, String contentHash);

    /**
     * Batch find memory items by content hashes (for batch deduplication)
     *
     * @param id Memory identifier
     * @param contentHashes Set of content hashes
     * @return List of matching MemoryItem
     */
    List<MemoryItem> getItemsByContentHashes(MemoryId id, Collection<String> contentHashes);

    /**
     * Batch get memory items by vector IDs
     *
     * @param id Memory identifier
     * @param vectorIds Set of vector IDs
     * @return List of matching MemoryItem
     */
    List<MemoryItem> getItemsByVectorIds(MemoryId id, Collection<String> vectorIds);

    /**
     * Batch get memory items by list of IDs
     *
     * @param id Memory identifier
     * @param itemIds List of item IDs
     * @return List of matching MemoryItem
     */
    List<MemoryItem> getItemsByIds(MemoryId id, List<Long> itemIds);

    /**
     * Get all memory items
     */
    List<MemoryItem> getAllItems(MemoryId id);

    /**
     * Get the most recent N memory items in reverse order of creation time
     */
    default List<MemoryItem> getRecentItems(MemoryId id, int limit) {
        return getAllItems(id).stream()
                .sorted(Comparator.comparing(MemoryItem::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Whether there are memory items
     */
    boolean hasItems(MemoryId id);

    /**
     * Get associated memory items by source data ID
     */
    List<MemoryItem> getItemsByRawDataId(MemoryId id, String rawDataId);

    /**
     * Get memory items by scope
     */
    List<MemoryItem> getItemsByScope(MemoryId id, MemoryScope scope);

    /**
     * Batch update existing memory items (only overwrite existing items, skip if not present)
     */
    void updateItems(MemoryId id, List<MemoryItem> items);

    /**
     * Delete memory item
     */
    void deleteItem(MemoryId id, Long itemId);

    // ===== MemoryInsightType =====

    /**
     * Save insight type
     */
    void saveInsightType(MemoryId id, MemoryInsightType insightType);

    /**
     * Get insight type by ID
     */
    Optional<MemoryInsightType> getInsightType(MemoryId id, String insightType);

    /**
     * Get all insight types
     */
    List<MemoryInsightType> getAllInsightTypes(MemoryId id);

    /**
     * Delete insight type
     */
    void deleteInsightType(MemoryId id, String insightType);

    // ===== MemoryInsight CRUD =====

    /**
     * Save insight
     */
    void saveInsight(MemoryId id, MemoryInsight insight);

    /**
     * Batch save insights
     */
    default void saveInsights(MemoryId id, List<MemoryInsight> insights) {
        insights.forEach(insight -> saveInsight(id, insight));
    }

    /**
     * Get insight by ID
     */
    Optional<MemoryInsight> getInsight(MemoryId id, Long insightId);

    /**
     * Get all insights
     */
    List<MemoryInsight> getAllInsights(MemoryId id);

    /**
     * Get insights by type
     */
    List<MemoryInsight> getInsightsByTypeId(MemoryId id, String insightType);

    /**
     * Get insights by scope
     */
    List<MemoryInsight> getInsightsByScope(MemoryId id, MemoryScope scope);

    /**
     * Delete insight
     */
    void deleteInsight(MemoryId id, Long insightId);

    // ===== Insight Tree Operations =====

    default List<MemoryInsight> getInsightsByTier(
            MemoryId memoryId, String type, InsightTier tier) {
        return getInsightsByTypeId(memoryId, type).stream()
                .filter(i -> tier.equals(i.tier()))
                .toList();
    }

    default List<MemoryInsight> getChildInsights(MemoryId memoryId, Long parentInsightId) {
        return getAllInsights(memoryId).stream()
                .filter(i -> parentInsightId.equals(i.parentInsightId()))
                .toList();
    }

    default Optional<MemoryInsight> getParentInsight(MemoryId memoryId, Long insightId) {
        return getInsight(memoryId, insightId)
                .filter(i -> i.parentInsightId() != null)
                .flatMap(i -> getInsight(memoryId, i.parentInsightId()));
    }

    default void archiveInsight(MemoryId memoryId, Long insightId) {
        deleteInsight(memoryId, insightId);
    }

    // ===== Shared Tree Query =====

    default Optional<MemoryInsight> getLeafByGroup(MemoryId id, String type, String group) {
        return getInsightsByTypeId(id, type).stream()
                .filter(i -> InsightTier.LEAF.equals(i.tier()) && group.equals(i.group()))
                .findFirst();
    }

    default Optional<MemoryInsight> getBranchByType(MemoryId id, String type) {
        return getInsightsByTypeId(id, type).stream()
                .filter(i -> InsightTier.BRANCH.equals(i.tier()))
                .findFirst();
    }

    default Optional<MemoryInsight> getRootByType(MemoryId id, String type) {
        return getInsightsByTypeId(id, type).stream()
                .filter(i -> InsightTier.ROOT.equals(i.tier()))
                .findFirst();
    }

    default List<MemoryInsight> getRoots(MemoryId id) {
        return getAllInsightsByTier(id, InsightTier.ROOT);
    }

    default List<MemoryInsight> getAllInsightsByTier(MemoryId id, InsightTier tier) {
        return getAllInsights(id).stream().filter(i -> tier.equals(i.tier())).toList();
    }
}
