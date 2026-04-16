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
package com.openmemind.ai.memory.core.retrieval.tier;

import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bidirectional Insight Tree Expander
 *
 * <p>The persisted insight structure is a shared layered model:
 *
 * <ul>
 *   <li>LEAF -> BRANCH is stored with {@code parentInsightId}</li>
 *   <li>ROOT -> BRANCH is stored with {@code ROOT.childInsightIds}</li>
 * </ul>
 *
 * <p>Because BRANCH nodes are shared across multiple ROOT nodes, upward ROOT lookup is derived
 * from ROOT child links rather than a single BRANCH parent pointer.
 */
public class InsightTreeExpander {

    private final int maxExpandedLeafsPerBranch;

    public InsightTreeExpander(int maxExpandedLeafsPerBranch) {
        this.maxExpandedLeafsPerBranch = maxExpandedLeafsPerBranch;
    }

    /**
     * Expand the insight nodes that are hit by the search
     *
     * @param hitIds         Set of insight IDs that are hit by the search (in string form)
     * @param insightIndex   All insights indexed by ID
     * @param queryEmbedding Query embedding (used for scoring the expanded LEAF)
     * @return Expansion result, including context insights and expanded leaf insights
     */
    public ExpandResult expand(
            Set<String> hitIds, Map<Long, MemoryInsight> insightIndex, List<Float> queryEmbedding) {

        if (hitIds == null || hitIds.isEmpty()) {
            return ExpandResult.empty();
        }

        // LinkedHashMap is used for natural deduplication, maintaining insertion order
        LinkedHashMap<String, ExpandedInsight> contextMap = new LinkedHashMap<>();
        LinkedHashMap<String, ExpandedInsight> expandedLeafMap = new LinkedHashMap<>();
        Map<Long, List<MemoryInsight>> rootParentIndex = buildRootParentIndex(insightIndex);

        for (String hitId : hitIds) {
            MemoryInsight hit = insightIndex.get(parseLong(hitId));
            if (hit == null || hit.tier() == null) {
                continue;
            }

            switch (hit.tier()) {
                case LEAF -> expandFromLeaf(hit, hitIds, insightIndex, rootParentIndex, contextMap);
                case BRANCH -> {
                    expandFromBranchUp(hit, hitIds, rootParentIndex, contextMap);
                    expandFromBranchDown(
                            hit, hitIds, insightIndex, queryEmbedding, expandedLeafMap);
                }
                case ROOT -> expandFromRootDown(hit, hitIds, insightIndex, contextMap);
            }
        }

        return new ExpandResult(
                new ArrayList<>(contextMap.values()), new ArrayList<>(expandedLeafMap.values()));
    }

    /**
     * Hit LEAF → Pull up parent BRANCH and grandparent ROOT
     */
    private void expandFromLeaf(
            MemoryInsight leaf,
            Set<String> hitIds,
            Map<Long, MemoryInsight> insightIndex,
            Map<Long, List<MemoryInsight>> rootParentIndex,
            LinkedHashMap<String, ExpandedInsight> contextMap) {

        if (leaf.parentInsightId() == null) {
            return;
        }

        MemoryInsight branch = insightIndex.get(leaf.parentInsightId());
        if (branch == null || branch.tier() != InsightTier.BRANCH) {
            return;
        }

        String branchId = String.valueOf(branch.id());
        if (!hitIds.contains(branchId) && !contextMap.containsKey(branchId)) {
            contextMap.put(
                    branchId, new ExpandedInsight(branchId, branch.pointsContent(), branch.tier()));
        }

        addRootParents(branch, hitIds, rootParentIndex, contextMap);
    }

    /**
     * Hit BRANCH → Pull up parent ROOT
     */
    private void expandFromBranchUp(
            MemoryInsight branch,
            Set<String> hitIds,
            Map<Long, List<MemoryInsight>> rootParentIndex,
            LinkedHashMap<String, ExpandedInsight> contextMap) {
        addRootParents(branch, hitIds, rootParentIndex, contextMap);
    }

    private void addRootParents(
            MemoryInsight branch,
            Set<String> hitIds,
            Map<Long, List<MemoryInsight>> rootParentIndex,
            LinkedHashMap<String, ExpandedInsight> contextMap) {
        for (MemoryInsight root : rootParentIndex.getOrDefault(branch.id(), List.of())) {
            String parentId = String.valueOf(root.id());
            if (hitIds.contains(parentId) || contextMap.containsKey(parentId)) {
                continue;
            }
            contextMap.put(
                    parentId, new ExpandedInsight(parentId, root.pointsContent(), root.tier()));
        }
    }

    /**
     * Hit BRANCH → Expand child LEAFs down, cut top-N based on cosine score
     */
    private void expandFromBranchDown(
            MemoryInsight branch,
            Set<String> hitIds,
            Map<Long, MemoryInsight> insightIndex,
            List<Float> queryEmbedding,
            LinkedHashMap<String, ExpandedInsight> expandedLeafMap) {

        List<Long> childIds = branch.childInsightIds();
        if (childIds == null || childIds.isEmpty()) {
            return;
        }

        boolean hasQueryEmbedding = queryEmbedding != null && !queryEmbedding.isEmpty();

        // Collect candidate LEAFs (skip already hit and expanded ones)
        List<ScoredChild> candidates = new ArrayList<>();
        for (Long childId : childIds) {
            String childIdStr = String.valueOf(childId);
            if (hitIds.contains(childIdStr) || expandedLeafMap.containsKey(childIdStr)) {
                continue;
            }
            MemoryInsight child = insightIndex.get(childId);
            if (child == null) {
                continue;
            }
            double score =
                    hasQueryEmbedding
                            ? cosineSimilarity(queryEmbedding, child.summaryEmbedding())
                            : 0.0;
            candidates.add(new ScoredChild(child, score));
        }

        if (hasQueryEmbedding) {
            // Sort by cosine similarity in descending order
            candidates.sort(Comparator.comparingDouble(ScoredChild::score).reversed());
        }

        // Cut top-N
        int limit = Math.min(maxExpandedLeafsPerBranch, candidates.size());
        for (int i = 0; i < limit; i++) {
            ScoredChild sc = candidates.get(i);
            String id = String.valueOf(sc.insight().id());
            if (!expandedLeafMap.containsKey(id)) {
                expandedLeafMap.put(
                        id,
                        new ExpandedInsight(id, sc.insight().pointsContent(), sc.insight().tier()));
            }
        }
    }

    /**
     * Hit ROOT → Expand child BRANCHes down (one level, do not continue to LEAF)
     */
    private void expandFromRootDown(
            MemoryInsight root,
            Set<String> hitIds,
            Map<Long, MemoryInsight> insightIndex,
            LinkedHashMap<String, ExpandedInsight> contextMap) {

        List<Long> childIds = root.childInsightIds();
        if (childIds == null || childIds.isEmpty()) {
            return;
        }

        for (Long childId : childIds) {
            String childIdStr = String.valueOf(childId);
            if (hitIds.contains(childIdStr) || contextMap.containsKey(childIdStr)) {
                continue;
            }
            MemoryInsight child = insightIndex.get(childId);
            if (child == null || child.tier() == null) {
                continue;
            }
            contextMap.put(
                    childIdStr,
                    new ExpandedInsight(childIdStr, child.pointsContent(), child.tier()));
        }
    }

    private Map<Long, List<MemoryInsight>> buildRootParentIndex(
            Map<Long, MemoryInsight> insightIndex) {
        Map<Long, List<MemoryInsight>> rootParentIndex = new LinkedHashMap<>();
        for (MemoryInsight insight : insightIndex.values()) {
            if (insight == null || insight.tier() != InsightTier.ROOT) {
                continue;
            }
            List<Long> childIds = insight.childInsightIds();
            if (childIds == null || childIds.isEmpty()) {
                continue;
            }
            for (Long childId : childIds) {
                rootParentIndex.computeIfAbsent(childId, ignored -> new ArrayList<>()).add(insight);
            }
        }
        return rootParentIndex;
    }

    private double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator > 0 ? dotProduct / denominator : 0.0;
    }

    private static Long parseLong(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    // ===== Inner records =====

    public record ExpandedInsight(String id, String text, InsightTier tier) {}

    public record ExpandResult(
            List<ExpandedInsight> contextInsights, List<ExpandedInsight> expandedLeafs) {

        public static ExpandResult empty() {
            return new ExpandResult(List.of(), List.of());
        }
    }

    private record ScoredChild(MemoryInsight insight, double score) {}
}
