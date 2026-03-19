package com.openmemind.ai.memory.core.extraction.insight.tree;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.store.MemoryStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single extraction process's Insight cache context
 *
 * <p>Cache the results of getInsightsByTier to avoid repeated queries in the same extraction. Lifecycle: a single generateTreeAwareInsight call.
 */
public class InsightTreeContext {

    private final MemoryStore store;
    private final MemoryId memoryId;
    private final String type;
    private final Map<InsightTier, List<MemoryInsight>> cache = new ConcurrentHashMap<>();

    public InsightTreeContext(MemoryStore store, MemoryId memoryId, String type) {
        this.store = store;
        this.memoryId = memoryId;
        this.type = type;
    }

    public List<MemoryInsight> getByTier(InsightTier tier) {
        return cache.computeIfAbsent(tier, t -> store.getInsightsByTier(memoryId, type, t));
    }

    public void invalidate(InsightTier tier) {
        cache.remove(tier);
    }

    public void invalidateAll() {
        cache.clear();
    }

    public List<MemoryInsight> getRoots() {
        return store.getRoots(memoryId);
    }

    public List<MemoryInsight> getAllBranches() {
        return store.getAllInsightsByTier(memoryId, InsightTier.BRANCH);
    }
}
