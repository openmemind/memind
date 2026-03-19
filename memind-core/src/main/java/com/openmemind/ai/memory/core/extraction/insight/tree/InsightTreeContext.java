/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
