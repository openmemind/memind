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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.InsightPointRef;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.PointOperation;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointGenerateResponse;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointOpsResponse;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.utils.IdUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("InsightTreeReorganizer")
class InsightTreeReorganizerTest {

    @Mock private InsightGenerator generator;
    @Mock private MemoryVector vector;
    @Mock private MemoryStore store;
    @Mock private InsightOperations insightOps;

    private final MemoryId memoryId = () -> "test-memory";
    private final IdUtils.SnowflakeIdGenerator idGenerator = IdUtils.snowflake();
    private BubbleTracker bubbleTracker;
    private InsightTreeReorganizer reorganizer;
    private InsightTreeConfig config;

    private static final String TYPE_NAME = "test-type";
    private static final String ROOT_TYPE_NAME = "user-profile";
    private static final String DIRTY_KEY = "test-memory::" + TYPE_NAME;

    @BeforeEach
    void setUp() {
        config = InsightTreeConfig.defaults(); // branchBubble=3, rootBubble=2, minBranches=2
        bubbleTracker = new BubbleTracker();
        when(store.insightOperations()).thenReturn(insightOps);
        reorganizer =
                new InsightTreeReorganizer(generator, vector, store, bubbleTracker, idGenerator);
    }

    @Nested
    @DisplayName("BRANCH delayed re-summarize but always link")
    class DeferredBranchCreation {

        @Test
        @DisplayName(
                "should link LEAF to BRANCH but not trigger re-summarize when dirtyCount does not"
                        + " reach threshold")
        void shouldLinkLeafButNotResummarizeBelowThreshold() {
            var leaf = createLeaf(1L, "The user likes coffee");

            when(insightOps.getInsightsByType(memoryId, TYPE_NAME)).thenReturn(List.of(leaf));
            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.empty());
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH)).thenReturn(List.of());
            when(insightOps.listInsightTypes()).thenReturn(List.of());

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            // BRANCH should be created and linked (through saveInsights)
            verify(insightOps, atLeastOnce())
                    .upsertInsights(
                            eq(memoryId),
                            argThat(
                                    list ->
                                            list.stream()
                                                    .anyMatch(
                                                            i ->
                                                                    i.tier() == InsightTier.BRANCH
                                                                            && i.childInsightIds()
                                                                                    .contains(
                                                                                            1L))));

            // LEAF should have parentInsightId set
            verify(insightOps, atLeastOnce())
                    .upsertInsights(
                            eq(memoryId),
                            argThat(
                                    list ->
                                            list.stream()
                                                    .anyMatch(
                                                            i ->
                                                                    i.tier() == InsightTier.LEAF
                                                                            && i.parentInsightId()
                                                                                    != null)));

            // should not trigger re-summarize
            verify(generator, never())
                    .generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class));
        }

        @Test
        @DisplayName(
                "should create BRANCH and trigger re-summarize when dirtyCount reaches threshold")
        void shouldCreateBranchWhenThresholdReached() {
            var leaf = createLeaf(3L, "Third piece of information");

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.empty());
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(
                            List.of(
                                    createLeaf(1L, "First piece"),
                                    createLeaf(2L, "Second piece"),
                                    leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH)).thenReturn(List.of());
            when(insightOps.listInsightTypes()).thenReturn(List.of());

            var point =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY, "Comprehensive summary", List.of());
            when(generator.generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointGenerateResponse(List.of(point))));
            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            // Pre-mark dirty 2 times (simulating previous LEAF updates)
            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            // Verify BRANCH is saved in batchLinkLeafsToBranch through saveInsights
            verify(insightOps, atLeastOnce())
                    .upsertInsights(
                            eq(memoryId),
                            argThat(
                                    list ->
                                            list.stream()
                                                    .anyMatch(
                                                            i ->
                                                                    i.tier() == InsightTier.BRANCH
                                                                            && TYPE_NAME.equals(
                                                                                    i.type()))));

            // Verify generateBranchSummary is called
            verify(generator)
                    .generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class));
        }
    }

    @Nested
    @DisplayName("LEAF→BRANCH batch link")
    class LeafToBranchBatchLink {

        @Test
        @DisplayName(
                "should link all LEAFs to BRANCH when threshold is reached (single saveInsights)")
        void shouldLinkAllLeafsToBranch() {
            var branch = createBranch(10L);
            var leaf1 = createLeaf(1L, "First piece");
            var leaf2 = createLeaf(2L, "Second piece");
            var leaf3 = createLeaf(3L, "Third piece");

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(List.of(leaf1, leaf2, leaf3));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch));
            when(insightOps.listInsightTypes()).thenReturn(List.of());

            var point =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY, "Comprehensive summary", List.of());
            when(generator.generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointGenerateResponse(List.of(point))));
            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            // Pre-mark dirty to reach threshold
            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf3, config);

            // Verify batchLinkLeafsToBranch saves in bulk (including branch + leaf parentId
            // updates)
            verify(insightOps, atLeastOnce())
                    .upsertInsights(
                            eq(memoryId),
                            argThat(
                                    list -> {
                                        var hasBranch =
                                                list.stream()
                                                        .anyMatch(
                                                                i ->
                                                                        i.tier()
                                                                                        == InsightTier
                                                                                                .BRANCH
                                                                                && i.childInsightIds()
                                                                                        .containsAll(
                                                                                                List
                                                                                                        .of(
                                                                                                                1L,
                                                                                                                2L,
                                                                                                                3L)));
                                        var hasLeafWithParent =
                                                list.stream()
                                                        .anyMatch(
                                                                i ->
                                                                        i.tier() == InsightTier.LEAF
                                                                                && Long.valueOf(10L)
                                                                                        .equals(
                                                                                                i
                                                                                                        .parentInsightId()));
                                        return hasBranch && hasLeafWithParent;
                                    }));
        }
    }

    @Nested
    @DisplayName("BRANCH re-summarize")
    class BranchResummarize {

        @Test
        @DisplayName("should trigger BRANCH re-summarize when dirtyCount reaches threshold")
        void shouldResummarizeBranchWhenThresholdReached() {
            var branch = createBranch(10L);
            var leaf = createLeaf(3L, "Third piece of information");

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(
                            List.of(
                                    createLeaf(1L, "First piece"),
                                    createLeaf(2L, "Second piece"),
                                    leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch));
            when(insightOps.listInsightTypes()).thenReturn(List.of());

            var point =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY, "Comprehensive summary", List.of());
            when(generator.generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointGenerateResponse(List.of(point))));
            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            // Pre-mark dirty 2 times (simulating previous updates)
            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            // Verify generateBranchSummary is called
            verify(generator)
                    .generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class));
        }

        @Test
        @DisplayName("should not trigger re-summarize when dirtyCount does not reach threshold")
        void shouldNotResummarizeBelowThreshold() {
            var leaf = createLeaf(1L, "First piece");

            when(insightOps.getInsightsByType(memoryId, TYPE_NAME)).thenReturn(List.of(leaf));
            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.empty());
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH)).thenReturn(List.of());
            when(insightOps.listInsightTypes()).thenReturn(List.of());

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            verify(generator, never())
                    .generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class));
        }

        @Test
        @DisplayName("branch point-op noop should not bubble ROOT synthesis")
        void branchPointOpNoopShouldNotBubbleRootSynthesis() {
            var existingPoint =
                    new InsightPoint(
                            "pt_branch_existing",
                            InsightPoint.PointType.SUMMARY,
                            "Existing branch summary",
                            List.of("1", "2"));
            var branch = createBranch(10L, List.of(existingPoint), List.of(1L, 2L, 3L));
            var branch2 = createBranchWithType(20L, "other-type");
            var root = createRoot(100L, ROOT_TYPE_NAME, List.of(10L, 20L));
            var leaf = createLeaf(3L, "Third piece of information");
            var rootInsightType = createRootInsightType(ROOT_TYPE_NAME);

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch));
            when(insightOps.getInsight(memoryId, 100L)).thenReturn(Optional.of(root));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(
                            List.of(
                                    createLeaf(1L, "First piece"),
                                    createLeaf(2L, "Second piece"),
                                    leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch, branch2));
            when(insightOps.listInsightTypes()).thenReturn(List.of(rootInsightType));
            when(insightOps.getRootByType(memoryId, ROOT_TYPE_NAME)).thenReturn(Optional.of(root));
            lenient()
                    .when(
                            generator.generateBranchPointOps(
                                    any(), anyList(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointOpsResponse(List.of())));

            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);
            var rootKey = "test-memory::root::" + ROOT_TYPE_NAME;
            bubbleTracker.markDirty(rootKey);

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            verify(generator)
                    .generateBranchPointOps(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class));
            verify(generator, never())
                    .generateBranchSummary(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class));
            verify(generator, never())
                    .generateRootSynthesis(
                            any(MemoryInsightType.class),
                            any(),
                            anyList(),
                            anyInt(),
                            nullable(String.class));
            assertThat(bubbleTracker.getDirtyCount(rootKey)).isEqualTo(1);
        }

        @Test
        @DisplayName("missing branch point-op response should fall back to full rewrite")
        void missingBranchPointOpsShouldFallBackToFullRewrite() {
            var branch = createBranch(10L);
            var leaf = createLeaf(3L, "Third piece of information");

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(
                            List.of(
                                    createLeaf(1L, "First piece"),
                                    createLeaf(2L, "Second piece"),
                                    leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch));
            when(insightOps.listInsightTypes()).thenReturn(List.of());
            when(generator.generateBranchPointOps(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(Mono.empty());
            when(generator.generateBranchSummary(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(
                            Mono.just(
                                    new InsightPointGenerateResponse(
                                            List.of(
                                                    new InsightPoint(
                                                            InsightPoint.PointType.SUMMARY,
                                                            "fallback",
                                                            List.of("1", "2"))))));
            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            verify(generator)
                    .generateBranchPointOps(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class));
            verify(generator)
                    .generateBranchSummary(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class));
        }

        @Test
        @DisplayName("fallback-required branch point ops should use full rewrite")
        void fallbackRequiredBranchPointOpsShouldUseFullRewrite() {
            var existingPoint =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY,
                            "Existing branch summary",
                            List.of("1", "2"));
            var branch = createBranch(10L, List.of(existingPoint), List.of(1L, 2L, 3L));
            var leaf = createLeaf(3L, "Third piece of information");

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(
                            List.of(
                                    createLeaf(1L, "First piece"),
                                    createLeaf(2L, "Second piece"),
                                    leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch));
            when(insightOps.listInsightTypes()).thenReturn(List.of());
            when(generator.generateBranchPointOps(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(
                            Mono.just(
                                    new InsightPointOpsResponse(
                                            List.of(
                                                    new PointOperation(
                                                            PointOperation.OpType.DELETE,
                                                            "pt_branch_existing",
                                                            null,
                                                            "drop")))));
            when(generator.generateBranchSummary(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(
                            Mono.just(
                                    new InsightPointGenerateResponse(
                                            List.of(
                                                    new InsightPoint(
                                                            InsightPoint.PointType.SUMMARY,
                                                            "fallback",
                                                            List.of("1", "2"))))));
            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            verify(generator)
                    .generateBranchPointOps(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class));
            verify(generator)
                    .generateBranchSummary(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class));
        }

        @Test
        @DisplayName("invalid branch point ops should fall back to full rewrite")
        void invalidBranchPointOpsShouldFallBackToFullRewrite() {
            var existingPoint =
                    new InsightPoint(
                            "pt_branch_existing",
                            InsightPoint.PointType.SUMMARY,
                            "Existing branch summary",
                            List.of("1", "2"));
            var branch = createBranch(10L, List.of(existingPoint), List.of(1L, 2L, 3L));
            var leaf = createLeaf(3L, "Third piece of information");

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(
                            List.of(
                                    createLeaf(1L, "First piece"),
                                    createLeaf(2L, "Second piece"),
                                    leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch));
            when(insightOps.listInsightTypes()).thenReturn(List.of());
            when(generator.generateBranchPointOps(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(
                            Mono.just(
                                    new InsightPointOpsResponse(
                                            List.of(
                                                    new PointOperation(
                                                            PointOperation.OpType.UPDATE,
                                                            "pt_branch_missing",
                                                            new InsightPoint(
                                                                    InsightPoint.PointType.SUMMARY,
                                                                    "invalid",
                                                                    List.of("1", "2")),
                                                            "bad target")))));
            when(generator.generateBranchSummary(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(
                            Mono.just(
                                    new InsightPointGenerateResponse(
                                            List.of(
                                                    new InsightPoint(
                                                            InsightPoint.PointType.SUMMARY,
                                                            "fallback",
                                                            List.of("1", "2"))))));
            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            verify(generator)
                    .generateBranchPointOps(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class));
            verify(generator)
                    .generateBranchSummary(
                            any(), anyList(), anyList(), anyInt(), nullable(String.class));
        }
    }

    @Nested
    @DisplayName("BRANCH→ROOT link (no need to re-summarize)")
    class BranchToRootLinkWithoutResummarize {

        @Test
        @DisplayName("should still link BRANCH to ROOT when BRANCH does not reach bubble threshold")
        void branchLinkedToRootEvenWithoutResummarize() {
            // Use high branchBubbleThreshold to ensure re-summarize is not triggered
            var highThresholdConfig = new InsightTreeConfig(100, 2, 2, 800);

            var branch1 = createBranchWithType(10L, TYPE_NAME);
            var branch2 = createBranchWithType(20L, "other-type");
            var leaf = createLeaf(1L, "New information");
            var rootInsightType = createRootInsightType(ROOT_TYPE_NAME);

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch1));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME)).thenReturn(List.of(leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch1, branch2));
            when(insightOps.listInsightTypes()).thenReturn(List.of(rootInsightType));
            when(insightOps.getRootByType(memoryId, ROOT_TYPE_NAME)).thenReturn(Optional.empty());

            reorganizer.onLeafUpdated(
                    memoryId, TYPE_NAME, createInsightType(), leaf, highThresholdConfig);

            // should not trigger BRANCH re-summarize (threshold not reached)
            verify(generator, never())
                    .generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class));

            // But ROOT should be created and include BRANCH as child
            verify(insightOps, atLeastOnce())
                    .upsertInsights(
                            eq(memoryId),
                            argThat(
                                    (List<MemoryInsight> list) ->
                                            list.stream()
                                                    .anyMatch(
                                                            i ->
                                                                    i.tier() == InsightTier.ROOT
                                                                            && i.childInsightIds()
                                                                                    .containsAll(
                                                                                            List.of(
                                                                                                    10L,
                                                                                                    20L)))));
        }
    }

    @Nested
    @DisplayName("ROOT creation and re-summarize")
    class RootManagement {

        @Test
        @DisplayName("should create ROOT when number of BRANCHes reaches minBranchesForRoot")
        void shouldCreateRootWhenEnoughBranches() {
            var branch1 = createBranchWithType(10L, "type-a");
            var branch2 = createBranchWithType(20L, "type-b");
            var leaf = createLeaf(3L, "New information");
            var rootInsightType = createRootInsightType(ROOT_TYPE_NAME);

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch1));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(List.of(createLeaf(1L, "Old information"), leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch1, branch2));
            when(insightOps.listInsightTypes()).thenReturn(List.of(rootInsightType));
            when(insightOps.getRootByType(memoryId, ROOT_TYPE_NAME)).thenReturn(Optional.empty());

            var point =
                    new InsightPoint(InsightPoint.PointType.SUMMARY, "BRANCH summary", List.of());
            when(generator.generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointGenerateResponse(List.of(point))));
            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            // Let branch bubble reach threshold
            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            // Verify ROOT is created (ensureRoot uses saveInsight to save the root itself)
            verify(insightOps, atLeastOnce())
                    .upsertInsights(
                            eq(memoryId),
                            argThat(
                                    (List<MemoryInsight> list) ->
                                            list.stream()
                                                    .anyMatch(i -> i.tier() == InsightTier.ROOT)));
        }

        @Test
        @DisplayName("should trigger re-summarize when ROOT dirtyCount reaches threshold")
        void shouldResummarizeRootWhenThresholdReached() {
            var branch1 = createBranchWithType(10L, TYPE_NAME);
            var branch2 = createBranchWithType(20L, "other-type");
            var root = createRoot(100L, ROOT_TYPE_NAME, List.of(10L, 20L));
            var leaf = createLeaf(3L, "New information");
            var rootInsightType = createRootInsightType(ROOT_TYPE_NAME);

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch1));
            when(insightOps.getInsight(memoryId, 100L)).thenReturn(Optional.of(root));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(List.of(createLeaf(1L, "Old information"), leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch1, branch2));
            when(insightOps.listInsightTypes()).thenReturn(List.of(rootInsightType));
            when(insightOps.getRootByType(memoryId, ROOT_TYPE_NAME)).thenReturn(Optional.of(root));

            var branchPoint =
                    new InsightPoint(InsightPoint.PointType.SUMMARY, "BRANCH summary", List.of());
            when(generator.generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointGenerateResponse(List.of(branchPoint))));

            var rootPoint =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY, "ROOT comprehensive", List.of());
            when(generator.generateRootSynthesis(
                            any(MemoryInsightType.class),
                            any(),
                            anyList(),
                            anyInt(),
                            nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointGenerateResponse(List.of(rootPoint))));
            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            // branch bubble reaches threshold
            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);
            // root bubble reaches threshold (new key format)
            var rootKey = "test-memory::root::" + ROOT_TYPE_NAME;
            bubbleTracker.markDirty(rootKey);

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            // ROOT re-summarize is executed asynchronously in a virtual thread, need to wait
            verify(generator, timeout(2000))
                    .generateRootSynthesis(
                            any(MemoryInsightType.class),
                            any(),
                            anyList(),
                            anyInt(),
                            nullable(String.class));
        }

        @Test
        @DisplayName(
                "ROOT re-summarize should skip embed and save when normalized points are unchanged")
        void rootResummarizeShouldSkipSaveWhenPointsAreUnchanged() {
            var branch =
                    createBranchWithType(10L, TYPE_NAME)
                            .withPoints(
                                    List.of(
                                            new InsightPoint(
                                                    "pt_branch_1",
                                                    InsightPoint.PointType.SUMMARY,
                                                    "Branch summary one",
                                                    List.of())));
            var branch2 =
                    createBranchWithType(20L, "other-type")
                            .withPoints(
                                    List.of(
                                            new InsightPoint(
                                                    "pt_branch_2",
                                                    InsightPoint.PointType.SUMMARY,
                                                    "Branch summary two",
                                                    List.of())));
            var existingRootPoint =
                    new InsightPoint(
                            "pt_root_existing",
                            InsightPoint.PointType.REASONING,
                            "ROOT comprehensive",
                            List.of(),
                            List.of(
                                    new InsightPointRef(10L, "pt_branch_1"),
                                    new InsightPointRef(20L, "pt_branch_2")),
                            Map.of("dimension", "convergence"));
            var root =
                    createRoot(100L, ROOT_TYPE_NAME, List.of(10L, 20L))
                            .withPoints(List.of(existingRootPoint))
                            .withVersion(1);
            var leaf = createLeaf(3L, "Third piece of information");
            var rootInsightType = createRootInsightType(ROOT_TYPE_NAME);

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch));
            when(insightOps.getInsight(memoryId, 100L)).thenReturn(Optional.of(root));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(
                            List.of(
                                    createLeaf(1L, "First piece"),
                                    createLeaf(2L, "Second piece"),
                                    leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch, branch2));
            when(insightOps.listInsightTypes()).thenReturn(List.of(rootInsightType));
            when(insightOps.getRootByType(memoryId, ROOT_TYPE_NAME)).thenReturn(Optional.of(root));

            when(generator.generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(
                            Mono.just(
                                    new InsightPointGenerateResponse(
                                            List.of(
                                                    new InsightPoint(
                                                            InsightPoint.PointType.SUMMARY,
                                                            "BRANCH summary",
                                                            List.of())))));

            when(generator.generateRootSynthesis(
                            any(MemoryInsightType.class),
                            any(),
                            anyList(),
                            anyInt(),
                            nullable(String.class)))
                    .thenReturn(
                            Mono.just(
                                    new InsightPointGenerateResponse(
                                            List.of(
                                                    new InsightPoint(
                                                            null,
                                                            InsightPoint.PointType.REASONING,
                                                            "ROOT comprehensive",
                                                            List.of(),
                                                            List.of(
                                                                    new InsightPointRef(
                                                                            10L, "pt_branch_1"),
                                                                    new InsightPointRef(
                                                                            20L, "pt_branch_2")),
                                                            Map.of("dimension", "convergence"))))));

            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty("test-memory::root::" + ROOT_TYPE_NAME);

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);
            reorganizer.drainRootTasks(memoryId, 2, TimeUnit.SECONDS);

            verify(vector, never()).embed("ROOT comprehensive");
            verify(insightOps, never())
                    .upsertInsights(
                            eq(memoryId),
                            argThat(
                                    list ->
                                            list.stream()
                                                    .anyMatch(
                                                            insight ->
                                                                    insight.tier()
                                                                                    == InsightTier
                                                                                            .ROOT
                                                                            && insight.version()
                                                                                    > 1)));
        }

        @Test
        @DisplayName("should keep ROOT dirty count when re-summarize fails")
        void shouldKeepRootDirtyCountWhenResummarizeFails() {
            var branch1 = createBranchWithType(10L, TYPE_NAME);
            var branch2 = createBranchWithType(20L, "other-type");
            var root = createRoot(100L, ROOT_TYPE_NAME, List.of(10L, 20L));
            var leaf = createLeaf(3L, "New information");
            var rootInsightType = createRootInsightType(ROOT_TYPE_NAME);
            var rootKey = "test-memory::root::" + ROOT_TYPE_NAME;

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch1));
            when(insightOps.getInsight(memoryId, 100L)).thenReturn(Optional.of(root));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(List.of(createLeaf(1L, "Old information"), leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch1, branch2));
            when(insightOps.listInsightTypes()).thenReturn(List.of(rootInsightType));
            when(insightOps.getRootByType(memoryId, ROOT_TYPE_NAME)).thenReturn(Optional.of(root));

            var branchPoint =
                    new InsightPoint(InsightPoint.PointType.SUMMARY, "BRANCH summary", List.of());
            when(generator.generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointGenerateResponse(List.of(branchPoint))));
            when(generator.generateRootSynthesis(
                            any(MemoryInsightType.class),
                            any(),
                            anyList(),
                            anyInt(),
                            nullable(String.class)))
                    .thenReturn(Mono.error(new IllegalStateException("root synthesis failed")));
            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(rootKey);

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            verify(generator, timeout(2000))
                    .generateRootSynthesis(
                            any(MemoryInsightType.class),
                            any(),
                            anyList(),
                            anyInt(),
                            nullable(String.class));
            assertThat(bubbleTracker.getDirtyCount(rootKey)).isEqualTo(2);
        }

        @Test
        @DisplayName("multiple ROOT-mode InsightType should independently build ROOT nodes")
        void shouldBuildMultipleRootsIndependently() {
            var branch1 = createBranchWithType(10L, TYPE_NAME);
            var branch2 = createBranchWithType(20L, "other-type");
            var leaf = createLeaf(3L, "New information");

            var rootType1 = createRootInsightType("user-profile");
            var rootType2 = createRootInsightType("behavior-analysis");
            var root1 = createRoot(100L, "user-profile", List.of(10L, 20L));
            var root2 = createRoot(200L, "behavior-analysis", List.of(10L, 20L));

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch1));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME))
                    .thenReturn(List.of(createLeaf(1L, "Old information"), leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch1, branch2));
            when(insightOps.listInsightTypes()).thenReturn(List.of(rootType1, rootType2));
            when(insightOps.getRootByType(memoryId, "user-profile")).thenReturn(Optional.of(root1));
            when(insightOps.getRootByType(memoryId, "behavior-analysis"))
                    .thenReturn(Optional.of(root2));
            when(insightOps.getInsight(memoryId, 100L)).thenReturn(Optional.of(root1));
            when(insightOps.getInsight(memoryId, 200L)).thenReturn(Optional.of(root2));

            var branchPoint =
                    new InsightPoint(InsightPoint.PointType.SUMMARY, "BRANCH summary", List.of());
            when(generator.generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointGenerateResponse(List.of(branchPoint))));

            var rootPoint =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY, "ROOT comprehensive", List.of());
            when(generator.generateRootSynthesis(
                            any(MemoryInsightType.class),
                            any(),
                            anyList(),
                            anyInt(),
                            nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointGenerateResponse(List.of(rootPoint))));
            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            // branch bubble reaches threshold
            bubbleTracker.markDirty(DIRTY_KEY);
            bubbleTracker.markDirty(DIRTY_KEY);
            // both root bubbles reach threshold
            bubbleTracker.markDirty("test-memory::root::user-profile");
            bubbleTracker.markDirty("test-memory::root::behavior-analysis");

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, createInsightType(), leaf, config);

            // both ROOTs should be re-summarized (asynchronously in virtual threads, need to wait)
            verify(generator, timeout(2000).atLeastOnce())
                    .generateRootSynthesis(
                            any(MemoryInsightType.class),
                            any(),
                            anyList(),
                            anyInt(),
                            nullable(String.class));
        }
    }

    @Nested
    @DisplayName("ROOT concurrency safety")
    class RootConcurrencySafety {

        @Test
        @DisplayName(
                "concurrent BRANCH updates should complete ROOT processing within lock, without"
                        + " losing childInsightIds")
        void shouldNotLoseChildIdsUnderConcurrentBranchUpdates() throws Exception {
            var branchA = createBranchWithType(10L, "type-a");
            var branchB = createBranchWithType(20L, "type-b");
            var root = createRoot(100L, ROOT_TYPE_NAME, List.of(10L, 20L));
            var leafA = createLeaf(1L, "Information A");
            var leafB = createLeaf(2L, "Information B");
            var rootInsightType = createRootInsightType(ROOT_TYPE_NAME);

            // Two types share the same set of mocks
            when(insightOps.getBranchByType(memoryId, "type-a")).thenReturn(Optional.of(branchA));
            when(insightOps.getBranchByType(memoryId, "type-b")).thenReturn(Optional.of(branchB));
            when(insightOps.getInsight(memoryId, 100L)).thenReturn(Optional.of(root));
            when(insightOps.getInsightsByType(eq(memoryId), eq("type-a")))
                    .thenReturn(List.of(leafA));
            when(insightOps.getInsightsByType(eq(memoryId), eq("type-b")))
                    .thenReturn(List.of(leafB));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branchA, branchB));
            when(insightOps.listInsightTypes()).thenReturn(List.of(rootInsightType));
            when(insightOps.getRootByType(memoryId, ROOT_TYPE_NAME)).thenReturn(Optional.of(root));

            var branchPoint =
                    new InsightPoint(InsightPoint.PointType.SUMMARY, "BRANCH summary", List.of());
            when(generator.generateBranchSummary(
                            any(), any(), anyList(), anyInt(), nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointGenerateResponse(List.of(branchPoint))));

            var rootPoint =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY, "ROOT comprehensive", List.of());
            when(generator.generateRootSynthesis(
                            any(MemoryInsightType.class),
                            any(),
                            anyList(),
                            anyInt(),
                            nullable(String.class)))
                    .thenReturn(Mono.just(new InsightPointGenerateResponse(List.of(rootPoint))));
            when(vector.embed(anyString())).thenReturn(Mono.just(List.of(0.1f, 0.2f)));

            // branch bubbles reach threshold
            var dirtyKeyA = "test-memory::type-a";
            var dirtyKeyB = "test-memory::type-b";
            bubbleTracker.markDirty(dirtyKeyA);
            bubbleTracker.markDirty(dirtyKeyA);
            bubbleTracker.markDirty(dirtyKeyB);
            bubbleTracker.markDirty(dirtyKeyB);
            // root bubble reaches threshold (new key format)
            var rootKey = "test-memory::root::" + ROOT_TYPE_NAME;
            bubbleTracker.markDirty(rootKey);

            var insightTypeA = createInsightTypeWithName("type-a");
            var insightTypeB = createInsightTypeWithName("type-b");
            var error = new AtomicReference<Throwable>();
            var latch = new CountDownLatch(2);
            var exec = Executors.newFixedThreadPool(2);

            exec.submit(
                    () -> {
                        try {
                            reorganizer.onLeafUpdated(
                                    memoryId, "type-a", insightTypeA, leafA, config);
                        } catch (Throwable t) {
                            error.compareAndSet(null, t);
                        } finally {
                            latch.countDown();
                        }
                    });
            exec.submit(
                    () -> {
                        try {
                            reorganizer.onLeafUpdated(
                                    memoryId, "type-b", insightTypeB, leafB, config);
                        } catch (Throwable t) {
                            error.compareAndSet(null, t);
                        } finally {
                            latch.countDown();
                        }
                    });

            latch.await();
            exec.shutdown();

            // No exceptions should occur
            assertThat(error.get()).isNull();

            // ROOT re-summarize should be called at least once (asynchronously in virtual threads,
            // need to wait)
            verify(generator, timeout(2000).atLeastOnce())
                    .generateRootSynthesis(
                            any(MemoryInsightType.class),
                            any(),
                            anyList(),
                            anyInt(),
                            nullable(String.class));
        }
    }

    // ===== Helper =====

    private MemoryInsight createLeaf(Long id, String content) {
        var now = Instant.now();
        return new MemoryInsight(
                id,
                "test-memory",
                TYPE_NAME,
                MemoryScope.USER,
                "leaf-" + id,
                List.of("tool"),
                List.of(new InsightPoint(InsightPoint.PointType.SUMMARY, content, List.of())),
                "group-a",
                now,
                List.of(0.1f, 0.2f),
                now,
                now,
                InsightTier.LEAF,
                null,
                List.of(),
                1);
    }

    private MemoryInsight createBranch(Long id) {
        return createBranch(id, List.of());
    }

    private MemoryInsight createBranch(Long id, List<Long> childIds) {
        return createBranch(id, List.of(), childIds);
    }

    private MemoryInsight createBranch(Long id, List<InsightPoint> points, List<Long> childIds) {
        var now = Instant.now();
        return new MemoryInsight(
                id,
                "test-memory",
                TYPE_NAME,
                MemoryScope.USER,
                "branch-" + TYPE_NAME,
                List.of("tool"),
                points,
                null,
                now,
                null,
                now,
                now,
                InsightTier.BRANCH,
                null,
                new ArrayList<>(childIds),
                1);
    }

    private MemoryInsight createBranchWithType(Long id, String type) {
        var now = Instant.now();
        return new MemoryInsight(
                id,
                "test-memory",
                type,
                MemoryScope.USER,
                "branch-" + type,
                List.of("tool"),
                List.of(),
                null,
                now,
                null,
                now,
                now,
                InsightTier.BRANCH,
                null,
                List.of(),
                1);
    }

    private MemoryInsight createRoot(Long id, String typeName, List<Long> childIds) {
        var now = Instant.now();
        return new MemoryInsight(
                id,
                "test-memory",
                typeName,
                MemoryScope.USER,
                "root-" + typeName,
                List.of(),
                List.of(),
                null,
                now,
                null,
                now,
                now,
                InsightTier.ROOT,
                null,
                new ArrayList<>(childIds),
                1);
    }

    private MemoryInsightType createInsightType() {
        return createInsightTypeWithName(TYPE_NAME);
    }

    private MemoryInsightType createInsightTypeWithName(String name) {
        return createInsightTypeWithNameAndScope(name, null);
    }

    private MemoryInsightType createInsightTypeWithNameAndScope(String name, MemoryScope scope) {
        return new MemoryInsightType(
                1L,
                name,
                "desc",
                null,
                List.of("tool"),
                400,
                null,
                Instant.now(),
                Instant.now(),
                InsightAnalysisMode.BRANCH,
                null,
                scope);
    }

    private MemoryInsightType createRootInsightType(String name) {
        return createRootInsightTypeWithScope(name, null);
    }

    private MemoryInsightType createRootInsightTypeWithScope(String name, MemoryScope scope) {
        return new MemoryInsightType(
                null,
                name,
                "Cross-type comprehensive insight",
                null,
                List.of(),
                800,
                null,
                Instant.now(),
                Instant.now(),
                InsightAnalysisMode.ROOT,
                null,
                scope);
    }

    @Nested
    @DisplayName("BRANCH/ROOT scope derived from InsightType")
    class ScopeDerivedFromInsightType {

        @Test
        @DisplayName("BRANCH created from AGENT scope InsightType should carry AGENT scope")
        void newBranchShouldUseAgentScopeFromInsightType() {
            var leaf = createLeaf(1L, "agent information");
            var agentInsightType = createInsightTypeWithNameAndScope(TYPE_NAME, MemoryScope.AGENT);

            when(insightOps.getInsightsByType(memoryId, TYPE_NAME)).thenReturn(List.of(leaf));
            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.empty());
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH)).thenReturn(List.of());
            when(insightOps.listInsightTypes()).thenReturn(List.of());

            reorganizer.onLeafUpdated(memoryId, TYPE_NAME, agentInsightType, leaf, config);

            verify(insightOps, atLeastOnce())
                    .upsertInsights(
                            eq(memoryId),
                            argThat(
                                    list ->
                                            list.stream()
                                                    .anyMatch(
                                                            i ->
                                                                    i.tier() == InsightTier.BRANCH
                                                                            && i.scope()
                                                                                    == MemoryScope
                                                                                            .AGENT)));
        }

        @Test
        @DisplayName("ROOT created from AGENT scope ROOT InsightType should carry AGENT scope")
        void ensureRootShouldUseAgentScopeFromRootInsightType() {
            var highThresholdConfig = new InsightTreeConfig(100, 2, 2, 800);
            var branch1 = createBranchWithType(10L, TYPE_NAME);
            var branch2 = createBranchWithType(20L, "other-type");
            var leaf = createLeaf(1L, "agent information");
            var agentRootType = createRootInsightTypeWithScope(ROOT_TYPE_NAME, MemoryScope.AGENT);

            when(insightOps.getBranchByType(memoryId, TYPE_NAME)).thenReturn(Optional.of(branch1));
            when(insightOps.getInsightsByType(memoryId, TYPE_NAME)).thenReturn(List.of(leaf));
            when(insightOps.getInsightsByTier(memoryId, InsightTier.BRANCH))
                    .thenReturn(List.of(branch1, branch2));
            when(insightOps.listInsightTypes()).thenReturn(List.of(agentRootType));
            when(insightOps.getRootByType(memoryId, ROOT_TYPE_NAME)).thenReturn(Optional.empty());

            reorganizer.onLeafUpdated(
                    memoryId, TYPE_NAME, createInsightType(), leaf, highThresholdConfig);

            verify(insightOps, atLeastOnce())
                    .upsertInsights(
                            eq(memoryId),
                            argThat(
                                    (List<MemoryInsight> list) ->
                                            list.stream()
                                                    .anyMatch(
                                                            i ->
                                                                    i.tier() == InsightTier.ROOT
                                                                            && i.scope()
                                                                                    == MemoryScope
                                                                                            .AGENT)));
        }
    }
}
