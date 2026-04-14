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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Tier 1: Insight Retrieval (LLM-based Type Routing)
 *
 * <p>Three-layer processing:
 * <ul>
 *   <li>ROOT (anticipation, user_portrait, interaction_guide) — always carried, as global context</li>
 *   <li>BRANCH (profile, preferences, etc.) — LLM routing selects related types</li>
 *   <li>LEAF (specific items under BRANCH) — cosine similarity sorting to get top-N</li>
 * </ul>
 *
 */
public class InsightTierRetriever {

    private static final Logger log = LoggerFactory.getLogger(InsightTierRetriever.class);

    private final MemoryStore memoryStore;
    private final MemoryVector memoryVector;
    private final InsightTypeRouter router;
    private final int maxExpandedLeafsPerBranch;
    private final Cache<String, List<MemoryInsight>> insightCache;

    public InsightTierRetriever(
            MemoryStore memoryStore, MemoryVector memoryVector, InsightTypeRouter router) {
        this(memoryStore, memoryVector, router, 0);
    }

    public InsightTierRetriever(
            MemoryStore memoryStore,
            MemoryVector memoryVector,
            InsightTypeRouter router,
            int maxExpandedLeafsPerBranch) {
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore must not be null");
        this.memoryVector = Objects.requireNonNull(memoryVector, "memoryVector must not be null");
        this.router = Objects.requireNonNull(router, "router must not be null");
        this.maxExpandedLeafsPerBranch = maxExpandedLeafsPerBranch;
        this.insightCache =
                Caffeine.newBuilder()
                        .maximumSize(50)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .build();
    }

    /** Invalidate the insight cache for the specified memoryId */
    public void invalidateCache(MemoryId memoryId) {
        if (memoryId != null) {
            insightCache.invalidate(memoryId.toIdentifier());
        }
    }

    /**
     * Execute Tier 1 retrieval
     *
     * @param context Query context
     * @param config  Retrieval configuration
     * @return Results containing ROOT + routed matching BRANCH + expanded LEAF
     */
    public Mono<TierResult> retrieve(QueryContext context, RetrievalConfig config) {
        if (!config.tier1().enabled()) {
            return Mono.just(TierResult.empty());
        }

        return executeRouting(context, config);
    }

    private Mono<TierResult> executeRouting(QueryContext context, RetrievalConfig config) {
        // ① Load all insights (cache)
        List<MemoryInsight> allInsights =
                insightCache.get(
                        context.memoryId().toIdentifier(),
                        key -> memoryStore.insightOperations().listInsights(context.memoryId()));

        var candidateInsights =
                allInsights.stream()
                        .filter(
                                insight ->
                                        insight.pointsContent() != null
                                                && !insight.pointsContent().isBlank())
                        .toList();

        if (candidateInsights.isEmpty()) {
            return Mono.just(TierResult.empty());
        }

        // Load insight types to distinguish ROOT / BRANCH
        List<MemoryInsightType> insightTypes = memoryStore.insightOperations().listInsightTypes();
        InsightTypeRuntimeView typeView = buildTypeView(insightTypes, candidateInsights);

        List<MemoryInsight> filteredInsights = candidateInsights;
        if (context.scope() != null) {
            filteredInsights =
                    filteredInsights.stream()
                            .filter(
                                    insight -> {
                                        var effectiveScope = resolveScope(insight, typeView);
                                        return effectiveScope == null
                                                || effectiveScope.equals(context.scope());
                                    })
                            .toList();
        }

        if (context.categories() != null && !context.categories().isEmpty()) {
            var allowedTypes =
                    context.categories().stream()
                            .flatMap(
                                    cat ->
                                            typeView
                                                    .categoryToTypes()
                                                    .getOrDefault(cat, Set.of())
                                                    .stream())
                            .collect(Collectors.toSet());
            filteredInsights =
                    filteredInsights.stream()
                            .filter(insight -> allowedTypes.contains(insight.type()))
                            .toList();
        }

        // ② Separate ROOT insights, always carried, score=1.0
        List<ScoredResult> rootResults = new ArrayList<>();
        List<MemoryInsight> branchInsights = new ArrayList<>();

        for (MemoryInsight insight : filteredInsights) {
            if (insight.tier() == InsightTier.LEAF) {
                continue;
            }
            InsightAnalysisMode mode = resolveAnalysisMode(insight, typeView);
            if (mode == InsightAnalysisMode.ROOT) {
                rootResults.add(
                        new ScoredResult(
                                ScoredResult.SourceType.INSIGHT,
                                String.valueOf(insight.id()),
                                insight.pointsContent(),
                                1.0f,
                                1.0));
            } else {
                branchInsights.add(insight);
            }
        }

        Map<String, String> routedTypeDescriptions =
                buildRoutedTypeDescriptions(branchInsights, typeView);

        // If there are no BRANCH insights or no routable types, return ROOT directly
        if (branchInsights.isEmpty() || routedTypeDescriptions.isEmpty()) {
            return Mono.just(new TierResult(rootResults, List.of(), List.of()));
        }

        // ③ Call router to select related BRANCH types
        return router.route(
                        context.searchQuery(),
                        context.conversationHistory(),
                        routedTypeDescriptions)
                .flatMap(
                        routedTypes -> {
                            // ④ If LLM returns an empty list → only return ROOT
                            if (routedTypes.isEmpty()) {
                                log.debug(
                                        "Type routing returned an empty list, only returning ROOT"
                                                + " insight");
                                return Mono.just(new TierResult(rootResults, List.of(), List.of()));
                            }

                            // ⑤ Filter matching BRANCH insights, score=1.0
                            List<ScoredResult> branchResults = new ArrayList<>();
                            List<MemoryInsight> matchedBranches = new ArrayList<>();
                            for (MemoryInsight insight : branchInsights) {
                                if (routedTypes.contains(insight.type())) {
                                    branchResults.add(
                                            new ScoredResult(
                                                    ScoredResult.SourceType.INSIGHT,
                                                    String.valueOf(insight.id()),
                                                    insight.pointsContent(),
                                                    1.0f,
                                                    1.0));
                                    matchedBranches.add(insight);
                                }
                            }

                            // ⑥ Collect all child LEAFs of selected BRANCH
                            Map<Long, MemoryInsight> insightIndex =
                                    allInsights.stream()
                                            .collect(
                                                    Collectors.toMap(
                                                            MemoryInsight::id,
                                                            Function.identity(),
                                                            (a, b) -> a));

                            List<MemoryInsight> leafInsights = new ArrayList<>();
                            for (MemoryInsight branch : matchedBranches) {
                                if (branch.childInsightIds() != null) {
                                    for (Long childId : branch.childInsightIds()) {
                                        MemoryInsight child = insightIndex.get(childId);
                                        if (child != null
                                                && child.pointsContent() != null
                                                && !child.pointsContent().isBlank()) {
                                            leafInsights.add(child);
                                        }
                                    }
                                }
                            }

                            // No LEAF → directly return ROOT + BRANCH
                            if (leafInsights.isEmpty() || maxExpandedLeafsPerBranch <= 0) {
                                var combined = new ArrayList<>(rootResults);
                                combined.addAll(branchResults);
                                return Mono.just(new TierResult(combined, List.of(), List.of()));
                            }

                            // ⑦ Real-time embed: query + leaf texts
                            Mono<List<Float>> queryEmbMono =
                                    memoryVector.embed(context.searchQuery());
                            Mono<List<List<Float>>> leafEmbMono =
                                    resolveLeafEmbeddings(leafInsights);

                            return Mono.zip(queryEmbMono, leafEmbMono)
                                    .map(
                                            tuple -> {
                                                List<Float> queryEmbedding = tuple.getT1();
                                                List<List<Float>> leafEmbeddings = tuple.getT2();

                                                // ⑧ For each BRANCH's child LEAF, do cosine sorting
                                                // to get top-N
                                                var expandedLeafs =
                                                        expandLeafs(
                                                                matchedBranches,
                                                                insightIndex,
                                                                leafInsights,
                                                                leafEmbeddings,
                                                                queryEmbedding);

                                                // ⑨ Merge ROOT + BRANCH + expanded LEAFs
                                                var combined = new ArrayList<>(rootResults);
                                                combined.addAll(branchResults);

                                                log.debug(
                                                        "Type routing completed: ROOT={},"
                                                                + " BRANCH={}, LEAF={}",
                                                        rootResults.size(),
                                                        branchResults.size(),
                                                        expandedLeafs.size());

                                                return new TierResult(
                                                        combined, List.of(), expandedLeafs);
                                            });
                        })
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "Tier 1 (Insight) retrieval failed, returning empty result", e);
                            return Mono.just(TierResult.empty());
                        });
    }

    private InsightTypeRuntimeView buildTypeView(
            List<MemoryInsightType> insightTypes, List<MemoryInsight> insights) {
        Map<String, InsightAnalysisMode> modes = new LinkedHashMap<>();
        Map<String, String> descriptions = new LinkedHashMap<>();
        Map<String, MemoryScope> scopes = new LinkedHashMap<>();
        Map<MemoryCategory, Set<String>> categoryToTypes = new EnumMap<>(MemoryCategory.class);
        Set<String> configuredTypes = new LinkedHashSet<>();

        for (MemoryInsightType type : insightTypes) {
            configuredTypes.add(type.name());
            modes.put(
                    type.name(),
                    type.insightAnalysisMode() != null
                            ? type.insightAnalysisMode()
                            : InsightAnalysisMode.BRANCH);
            descriptions.put(type.name(), resolveDescription(type.name(), type.description()));
            if (type.scope() != null) {
                scopes.put(type.name(), type.scope());
            }
            if (type.categories() == null) {
                continue;
            }
            for (String category : type.categories()) {
                MemoryCategory.byName(category)
                        .ifPresent(
                                cat ->
                                        categoryToTypes
                                                .computeIfAbsent(
                                                        cat, ignored -> new LinkedHashSet<>())
                                                .add(type.name()));
            }
        }

        for (MemoryInsight insight : insights) {
            if (configuredTypes.contains(insight.type())) {
                continue;
            }
            descriptions.putIfAbsent(
                    insight.type(), resolveDescription(insight.type(), insight.name()));
            if (insight.scope() != null) {
                scopes.putIfAbsent(insight.type(), insight.scope());
            }
            if (insight.categories() == null) {
                continue;
            }
            for (String category : insight.categories()) {
                MemoryCategory.byName(category)
                        .ifPresent(
                                cat ->
                                        categoryToTypes
                                                .computeIfAbsent(
                                                        cat, ignored -> new LinkedHashSet<>())
                                                .add(insight.type()));
            }
        }

        return new InsightTypeRuntimeView(
                Set.copyOf(configuredTypes),
                Map.copyOf(modes),
                Map.copyOf(descriptions),
                Map.copyOf(scopes),
                immutableCategoryToTypes(categoryToTypes));
    }

    private Map<MemoryCategory, Set<String>> immutableCategoryToTypes(
            Map<MemoryCategory, Set<String>> categoryToTypes) {
        Map<MemoryCategory, Set<String>> immutable = new EnumMap<>(MemoryCategory.class);
        categoryToTypes.forEach((category, types) -> immutable.put(category, Set.copyOf(types)));
        return Map.copyOf(immutable);
    }

    private Map<String, String> buildRoutedTypeDescriptions(
            List<MemoryInsight> branchInsights, InsightTypeRuntimeView typeView) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (MemoryInsight insight : branchInsights) {
            if (resolveAnalysisMode(insight, typeView) != InsightAnalysisMode.BRANCH) {
                continue;
            }
            descriptions.putIfAbsent(
                    insight.type(),
                    typeView.descriptions()
                            .getOrDefault(
                                    insight.type(),
                                    resolveDescription(insight.type(), insight.name())));
        }
        return Map.copyOf(descriptions);
    }

    private InsightAnalysisMode resolveAnalysisMode(
            MemoryInsight insight, InsightTypeRuntimeView typeView) {
        if (typeView.configuredTypes().contains(insight.type())) {
            return typeView.modes().getOrDefault(insight.type(), InsightAnalysisMode.BRANCH);
        }
        return insight.tier() == InsightTier.ROOT
                ? InsightAnalysisMode.ROOT
                : InsightAnalysisMode.BRANCH;
    }

    private MemoryScope resolveScope(MemoryInsight insight, InsightTypeRuntimeView typeView) {
        if (typeView.configuredTypes().contains(insight.type())) {
            return typeView.scopes().get(insight.type());
        }
        return insight.scope();
    }

    private String resolveDescription(String typeName, String description) {
        return description != null && !description.isBlank() ? description : typeName;
    }

    private Mono<List<List<Float>>> resolveLeafEmbeddings(List<MemoryInsight> leafInsights) {
        var resolvedEmbeddings = new ArrayList<List<Float>>(leafInsights.size());
        for (int i = 0; i < leafInsights.size(); i++) {
            resolvedEmbeddings.add(null);
        }

        List<PendingLeafEmbedding> missingEmbeddings = new ArrayList<>();
        for (int i = 0; i < leafInsights.size(); i++) {
            var leaf = leafInsights.get(i);
            if (leaf.summaryEmbedding() != null && !leaf.summaryEmbedding().isEmpty()) {
                resolvedEmbeddings.set(i, leaf.summaryEmbedding());
            } else {
                missingEmbeddings.add(new PendingLeafEmbedding(i, leaf.pointsContent()));
            }
        }

        if (missingEmbeddings.isEmpty()) {
            return Mono.just(List.copyOf(resolvedEmbeddings));
        }

        var textsToEmbed = missingEmbeddings.stream().map(PendingLeafEmbedding::text).toList();
        return memoryVector
                .embedAll(textsToEmbed)
                .map(
                        embeddedLeaves -> {
                            for (int i = 0; i < missingEmbeddings.size(); i++) {
                                resolvedEmbeddings.set(
                                        missingEmbeddings.get(i).index(), embeddedLeaves.get(i));
                            }
                            return List.copyOf(resolvedEmbeddings);
                        });
    }

    /**
     * Sort the child LEAFs of the selected BRANCH by cosine similarity to get top-N
     */
    private List<InsightTreeExpander.ExpandedInsight> expandLeafs(
            List<MemoryInsight> matchedBranches,
            Map<Long, MemoryInsight> insightIndex,
            List<MemoryInsight> leafInsights,
            List<List<Float>> leafEmbeddings,
            List<Float> queryEmbedding) {

        // Build LEAF id → embedding index
        Map<Long, List<Float>> leafEmbeddingMap = new LinkedHashMap<>();
        for (int i = 0; i < leafInsights.size(); i++) {
            leafEmbeddingMap.put(leafInsights.get(i).id(), leafEmbeddings.get(i));
        }

        List<InsightTreeExpander.ExpandedInsight> result = new ArrayList<>();

        for (MemoryInsight branch : matchedBranches) {
            if (branch.childInsightIds() == null || branch.childInsightIds().isEmpty()) {
                continue;
            }

            // Collect candidate LEAFs and calculate similarity
            List<ScoredLeaf> candidates = new ArrayList<>();
            for (Long childId : branch.childInsightIds()) {
                MemoryInsight child = insightIndex.get(childId);
                if (child == null
                        || child.pointsContent() == null
                        || child.pointsContent().isBlank()) {
                    continue;
                }
                List<Float> childEmb = leafEmbeddingMap.get(childId);
                double score =
                        (childEmb != null) ? cosineSimilarity(queryEmbedding, childEmb) : 0.0;
                candidates.add(new ScoredLeaf(child, score));
            }

            // Sort by cosine in descending order, take top-N
            candidates.sort(Comparator.comparingDouble(ScoredLeaf::score).reversed());
            int limit = Math.min(maxExpandedLeafsPerBranch, candidates.size());
            for (int i = 0; i < limit; i++) {
                ScoredLeaf sl = candidates.get(i);
                result.add(
                        new InsightTreeExpander.ExpandedInsight(
                                String.valueOf(sl.insight().id()),
                                sl.insight().pointsContent(),
                                sl.insight().tier()));
            }
        }

        return result;
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

    private record PendingLeafEmbedding(int index, String text) {}

    private record ScoredLeaf(MemoryInsight insight, double score) {}

    private record InsightTypeRuntimeView(
            Set<String> configuredTypes,
            Map<String, InsightAnalysisMode> modes,
            Map<String, String> descriptions,
            Map<String, MemoryScope> scopes,
            Map<MemoryCategory, Set<String>> categoryToTypes) {}
}
