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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTreeExpander.ExpandResult;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTreeExpander.ExpandedInsight;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InsightTreeExpander Unit Test")
class InsightTreeExpanderTest {

    private InsightTreeExpander expander;

    @BeforeEach
    void setUp() {
        expander = new InsightTreeExpander(3);
    }

    // ===== Helper methods =====

    private MemoryInsight buildInsight(
            Long id, InsightTier tier, String content, Long parentId, List<Long> childIds) {
        return new MemoryInsight(
                id,
                "user1:agent1",
                "type",
                MemoryScope.USER,
                "name",
                List.of(),
                List.of(new InsightPoint(InsightPoint.PointType.SUMMARY, content, List.of())),
                null,
                Instant.now(),
                null,
                Instant.now(),
                Instant.now(),
                tier,
                parentId,
                childIds,
                1);
    }

    private MemoryInsight buildInsightWithEmbedding(
            Long id,
            InsightTier tier,
            String content,
            Long parentId,
            List<Long> childIds,
            List<Float> embedding) {
        return new MemoryInsight(
                id,
                "user1:agent1",
                "type",
                MemoryScope.USER,
                "name",
                List.of(),
                List.of(new InsightPoint(InsightPoint.PointType.SUMMARY, content, List.of())),
                null,
                Instant.now(),
                embedding,
                Instant.now(),
                Instant.now(),
                tier,
                parentId,
                childIds,
                1);
    }

    private Map<Long, MemoryInsight> indexOf(MemoryInsight... insights) {
        return java.util.Arrays.stream(insights)
                .collect(Collectors.toMap(MemoryInsight::id, Function.identity()));
    }

    private List<String> ids(List<ExpandedInsight> list) {
        return list.stream().map(ExpandedInsight::id).toList();
    }

    // ===== Test groups =====

    @Nested
    @DisplayName("Bottom Up Tests")
    class BottomUpTests {

        @Test
        @DisplayName("Hit LEAF -> Should pull parent BRANCH and grandparent ROOT")
        void hitLeaf_shouldPullParentBranchAndGrandparentRoot() {
            var leaf = buildInsight(1L, InsightTier.LEAF, "leaf content", 10L, List.of());
            var branch = buildInsight(10L, InsightTier.BRANCH, "branch content", null, List.of(1L));
            var root = buildInsight(100L, InsightTier.ROOT, "root content", null, List.of(10L));

            Map<Long, MemoryInsight> index = indexOf(leaf, branch, root);
            Set<String> hitIds = Set.of("1");

            ExpandResult result = expander.expand(hitIds, index, List.of());

            assertThat(ids(result.contextInsights())).containsExactlyInAnyOrder("10", "100");
            assertThat(result.contextInsights())
                    .extracting(ExpandedInsight::tier)
                    .containsExactlyInAnyOrder(InsightTier.BRANCH, InsightTier.ROOT);
        }

        @Test
        @DisplayName(
                "Hit LEAF but parent already in hitIds -> Should skip parent, continue to pull"
                        + " ROOT")
        void hitLeaf_parentAlreadyInHits_shouldSkipParentAndPullRoot() {
            var leaf = buildInsight(1L, InsightTier.LEAF, "leaf content", 10L, List.of());
            var branch = buildInsight(10L, InsightTier.BRANCH, "branch content", null, List.of(1L));
            var root = buildInsight(100L, InsightTier.ROOT, "root content", null, List.of(10L));

            Map<Long, MemoryInsight> index = indexOf(leaf, branch, root);
            Set<String> hitIds = Set.of("1", "10");

            ExpandResult result = expander.expand(hitIds, index, List.of());

            // parent BRANCH (10) already in hits, should only pull ROOT (100)
            assertThat(ids(result.contextInsights())).containsExactly("100");
        }

        @Test
        @DisplayName("Hit BRANCH -> Should pull parent ROOT")
        void hitBranch_shouldPullParentRoot() {
            var branch =
                    buildInsight(10L, InsightTier.BRANCH, "branch content", null, List.of(1L, 2L));
            var root = buildInsight(100L, InsightTier.ROOT, "root content", null, List.of(10L));
            var leaf1 =
                    buildInsightWithEmbedding(
                            1L,
                            InsightTier.LEAF,
                            "leaf1",
                            10L,
                            List.of(),
                            List.of(0.1f, 0.2f, 0.3f));
            var leaf2 =
                    buildInsightWithEmbedding(
                            2L,
                            InsightTier.LEAF,
                            "leaf2",
                            10L,
                            List.of(),
                            List.of(0.4f, 0.5f, 0.6f));

            Map<Long, MemoryInsight> index = indexOf(branch, root, leaf1, leaf2);
            Set<String> hitIds = Set.of("10");

            ExpandResult result = expander.expand(hitIds, index, List.of(0.1f, 0.2f, 0.3f));

            // ROOT should appear in context
            assertThat(ids(result.contextInsights())).contains("100");
        }

        @Test
        @DisplayName(
                "Hit BRANCH with persisted shared-root shape should pull ROOT from root child ids")
        void hitBranch_persistedSharedRootShape_shouldPullRootFromRootChildIds() {
            var leaf = buildInsight(1L, InsightTier.LEAF, "leaf content", 10L, List.of());
            var branch = buildInsight(10L, InsightTier.BRANCH, "branch content", null, List.of(1L));
            var root = buildInsight(100L, InsightTier.ROOT, "root content", null, List.of(10L));

            Map<Long, MemoryInsight> index = indexOf(leaf, branch, root);
            ExpandResult result = expander.expand(Set.of("10"), index, List.of());

            assertThat(ids(result.contextInsights())).containsExactly("100");
        }

        @Test
        @DisplayName("Hit LEAF with persisted shared-root shape should pull BRANCH and ROOT")
        void hitLeaf_persistedSharedRootShape_shouldPullBranchAndRoot() {
            var leaf = buildInsight(1L, InsightTier.LEAF, "leaf content", 10L, List.of());
            var branch = buildInsight(10L, InsightTier.BRANCH, "branch content", null, List.of(1L));
            var root = buildInsight(100L, InsightTier.ROOT, "root content", null, List.of(10L));

            Map<Long, MemoryInsight> index = indexOf(leaf, branch, root);
            ExpandResult result = expander.expand(Set.of("1"), index, List.of());

            assertThat(ids(result.contextInsights())).containsExactlyInAnyOrder("10", "100");
        }

        @Test
        @DisplayName("Hit BRANCH shared by two ROOT nodes should pull both ROOT contexts")
        void hitBranch_sharedByTwoRoots_shouldPullBothRoots() {
            var leaf = buildInsight(1L, InsightTier.LEAF, "leaf content", 10L, List.of());
            var branch = buildInsight(10L, InsightTier.BRANCH, "branch content", null, List.of(1L));
            var profileRoot =
                    buildInsight(100L, InsightTier.ROOT, "profile root", null, List.of(10L));
            var interactionRoot =
                    buildInsight(200L, InsightTier.ROOT, "interaction root", null, List.of(10L));

            Map<Long, MemoryInsight> index = indexOf(leaf, branch, profileRoot, interactionRoot);
            ExpandResult result = expander.expand(Set.of("10"), index, List.of());

            assertThat(ids(result.contextInsights())).containsExactlyInAnyOrder("100", "200");
        }

        @Test
        @DisplayName("Hit LEAF and parentInsightId is null -> Do not pull up")
        void hitLeaf_noParent_shouldNotPullUp() {
            var leaf = buildInsight(1L, InsightTier.LEAF, "leaf content", null, List.of());

            Map<Long, MemoryInsight> index = indexOf(leaf);
            Set<String> hitIds = Set.of("1");

            ExpandResult result = expander.expand(hitIds, index, List.of());

            assertThat(result.contextInsights()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Top Down Tests")
    class TopDownTests {

        @Test
        @DisplayName("Hit BRANCH -> Expand child LEAFs, take top-N by cosine scoring")
        void hitBranch_shouldExpandChildLeafsWithCosineScoringTopN() {
            // Create a BRANCH with 5 child LEAFs, maxExpandedLeafsPerBranch = 3
            var branch =
                    buildInsight(
                            10L, InsightTier.BRANCH, "branch", 100L, List.of(1L, 2L, 3L, 4L, 5L));
            var root = buildInsight(100L, InsightTier.ROOT, "root", null, List.of(10L));

            // Query embedding = [1, 0, 0] - leaf1 should have highest similarity
            List<Float> queryEmbedding = List.of(1.0f, 0.0f, 0.0f);

            // Leaf embeddings designed so leaf1 > leaf3 > leaf2 > leaf4 > leaf5
            var leaf1 =
                    buildInsightWithEmbedding(
                            1L,
                            InsightTier.LEAF,
                            "leaf1",
                            10L,
                            List.of(),
                            List.of(1.0f, 0.0f, 0.0f));
            var leaf2 =
                    buildInsightWithEmbedding(
                            2L,
                            InsightTier.LEAF,
                            "leaf2",
                            10L,
                            List.of(),
                            List.of(0.5f, 0.5f, 0.5f));
            var leaf3 =
                    buildInsightWithEmbedding(
                            3L,
                            InsightTier.LEAF,
                            "leaf3",
                            10L,
                            List.of(),
                            List.of(0.9f, 0.1f, 0.0f));
            var leaf4 =
                    buildInsightWithEmbedding(
                            4L,
                            InsightTier.LEAF,
                            "leaf4",
                            10L,
                            List.of(),
                            List.of(0.3f, 0.8f, 0.0f));
            var leaf5 =
                    buildInsightWithEmbedding(
                            5L,
                            InsightTier.LEAF,
                            "leaf5",
                            10L,
                            List.of(),
                            List.of(0.0f, 0.0f, 1.0f));

            Map<Long, MemoryInsight> index =
                    indexOf(branch, root, leaf1, leaf2, leaf3, leaf4, leaf5);
            Set<String> hitIds = Set.of("10");

            ExpandResult result = expander.expand(hitIds, index, queryEmbedding);

            // Should take top-3 by cosine similarity: leaf1, leaf3, leaf2
            assertThat(result.expandedLeafs()).hasSize(3);
            assertThat(ids(result.expandedLeafs())).containsExactly("1", "3", "2");
        }

        @Test
        @DisplayName("Hit ROOT -> Expand child BRANCHes (do not continue to LEAF)")
        void hitRoot_shouldExpandChildBranchesOnly() {
            var root = buildInsight(100L, InsightTier.ROOT, "root", null, List.of(10L, 20L));
            var branch1 = buildInsight(10L, InsightTier.BRANCH, "branch1", 100L, List.of(1L));
            var branch2 = buildInsight(20L, InsightTier.BRANCH, "branch2", 100L, List.of(2L));
            var leaf1 = buildInsight(1L, InsightTier.LEAF, "leaf1", 10L, List.of());
            var leaf2 = buildInsight(2L, InsightTier.LEAF, "leaf2", 20L, List.of());

            Map<Long, MemoryInsight> index = indexOf(root, branch1, branch2, leaf1, leaf2);
            Set<String> hitIds = Set.of("100");

            ExpandResult result = expander.expand(hitIds, index, List.of());

            // BRANCHes should be in context, LEAFs should NOT be expanded
            assertThat(ids(result.contextInsights())).containsExactlyInAnyOrder("10", "20");
            assertThat(result.expandedLeafs()).isEmpty();
        }

        @Test
        @DisplayName("Hit BRANCH and childInsightIds is null -> Do not expand down")
        void hitBranch_noChildren_shouldNotExpandDown() {
            var branch = buildInsight(10L, InsightTier.BRANCH, "branch", 100L, null);
            var root = buildInsight(100L, InsightTier.ROOT, "root", null, List.of(10L));

            Map<Long, MemoryInsight> index = indexOf(branch, root);
            Set<String> hitIds = Set.of("10");

            ExpandResult result = expander.expand(hitIds, index, List.of());

            assertThat(result.expandedLeafs()).isEmpty();
            // ROOT should still be pulled up
            assertThat(ids(result.contextInsights())).contains("100");
        }

        @Test
        @DisplayName("When queryEmbedding is null, should take first N child nodes (no sorting)")
        void hitBranch_nullQueryEmbedding_shouldTakeFirstN() {
            var branch =
                    buildInsight(10L, InsightTier.BRANCH, "branch", null, List.of(1L, 2L, 3L, 4L));
            var leaf1 = buildInsight(1L, InsightTier.LEAF, "leaf1", 10L, List.of());
            var leaf2 = buildInsight(2L, InsightTier.LEAF, "leaf2", 10L, List.of());
            var leaf3 = buildInsight(3L, InsightTier.LEAF, "leaf3", 10L, List.of());
            var leaf4 = buildInsight(4L, InsightTier.LEAF, "leaf4", 10L, List.of());

            Map<Long, MemoryInsight> index = indexOf(branch, leaf1, leaf2, leaf3, leaf4);
            Set<String> hitIds = Set.of("10");

            // null query embedding
            ExpandResult result = expander.expand(hitIds, index, null);

            // maxExpandedLeafsPerBranch = 3, should take first 3
            assertThat(result.expandedLeafs()).hasSize(3);
            assertThat(ids(result.expandedLeafs())).containsExactly("1", "2", "3");
        }

        @Test
        @DisplayName("Child LEAF's summaryEmbedding is null -> cosine returns 0.0")
        void childLeafWithNullEmbedding_shouldScoreZero() {
            var branch = buildInsight(10L, InsightTier.BRANCH, "branch", null, List.of(1L, 2L));
            // leaf1 has embedding, leaf2 does not
            var leaf1 =
                    buildInsightWithEmbedding(
                            1L,
                            InsightTier.LEAF,
                            "leaf1",
                            10L,
                            List.of(),
                            List.of(1.0f, 0.0f, 0.0f));
            var leaf2 = buildInsight(2L, InsightTier.LEAF, "leaf2", 10L, List.of());

            Map<Long, MemoryInsight> index = indexOf(branch, leaf1, leaf2);
            Set<String> hitIds = Set.of("10");

            ExpandResult result = expander.expand(hitIds, index, List.of(1.0f, 0.0f, 0.0f));

            // Both should be included (maxExpandedLeafsPerBranch=3 > 2 children)
            // leaf1 should come first (higher cosine), leaf2 (cosine=0) second
            assertThat(result.expandedLeafs()).hasSize(2);
            assertThat(ids(result.expandedLeafs()).getFirst()).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("Deduplication Tests")
    class DeduplicationTests {

        @Test
        @DisplayName("Two LEAFs share the same parent BRANCH -> BRANCH should appear only once")
        void twoLeafsShareParent_branchShouldAppearOnce() {
            var leaf1 = buildInsight(1L, InsightTier.LEAF, "leaf1", 10L, List.of());
            var leaf2 = buildInsight(2L, InsightTier.LEAF, "leaf2", 10L, List.of());
            var branch = buildInsight(10L, InsightTier.BRANCH, "branch", 100L, List.of(1L, 2L));
            var root = buildInsight(100L, InsightTier.ROOT, "root", null, List.of(10L));

            Map<Long, MemoryInsight> index = indexOf(leaf1, leaf2, branch, root);
            Set<String> hitIds = Set.of("1", "2");

            ExpandResult result = expander.expand(hitIds, index, List.of());

            // BRANCH 10 should appear only once; ROOT 100 should also appear once
            long branchCount =
                    result.contextInsights().stream().filter(e -> e.id().equals("10")).count();
            long rootCount =
                    result.contextInsights().stream().filter(e -> e.id().equals("100")).count();

            assertThat(branchCount).isEqualTo(1);
            assertThat(rootCount).isEqualTo(1);
        }

        @Test
        @DisplayName("When BRANCH expands down, skip LEAFs already in hitIds")
        void branchExpandDown_shouldSkipLeafsAlreadyInHits() {
            var branch = buildInsight(10L, InsightTier.BRANCH, "branch", null, List.of(1L, 2L, 3L));
            var leaf1 =
                    buildInsightWithEmbedding(
                            1L,
                            InsightTier.LEAF,
                            "leaf1",
                            10L,
                            List.of(),
                            List.of(0.9f, 0.1f, 0.0f));
            var leaf2 =
                    buildInsightWithEmbedding(
                            2L,
                            InsightTier.LEAF,
                            "leaf2",
                            10L,
                            List.of(),
                            List.of(0.5f, 0.5f, 0.0f));
            var leaf3 =
                    buildInsightWithEmbedding(
                            3L,
                            InsightTier.LEAF,
                            "leaf3",
                            10L,
                            List.of(),
                            List.of(0.1f, 0.9f, 0.0f));

            Map<Long, MemoryInsight> index = indexOf(branch, leaf1, leaf2, leaf3);
            // leaf1 is already a hit
            Set<String> hitIds = Set.of("10", "1");

            ExpandResult result = expander.expand(hitIds, index, List.of(1.0f, 0.0f, 0.0f));

            // leaf1 should be skipped since it's in hitIds
            assertThat(ids(result.expandedLeafs())).doesNotContain("1");
            assertThat(ids(result.expandedLeafs())).containsExactlyInAnyOrder("2", "3");
        }
    }

    @Nested
    @DisplayName("Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("Insight with null tier -> Do not expand")
        void nullTier_shouldNotExpand() {
            var legacyInsight = buildInsight(1L, null, "legacy content", 10L, List.of());
            var branch = buildInsight(10L, InsightTier.BRANCH, "branch", null, List.of(1L));

            Map<Long, MemoryInsight> index = indexOf(legacyInsight, branch);
            Set<String> hitIds = Set.of("1");

            ExpandResult result = expander.expand(hitIds, index, List.of());

            // Should not expand up (tier is null)
            assertThat(result.contextInsights()).isEmpty();
            assertThat(result.expandedLeafs()).isEmpty();
        }

        @Test
        @DisplayName("When hitIds is empty, should return empty result")
        void emptyHitIds_shouldReturnEmpty() {
            ExpandResult result = expander.expand(Set.of(), Map.of(), List.of());

            assertThat(result.contextInsights()).isEmpty();
            assertThat(result.expandedLeafs()).isEmpty();
        }

        @Test
        @DisplayName("When hitId cannot be found in insightIndex -> Should skip")
        void hitIdNotInIndex_shouldSkip() {
            Set<String> hitIds = Set.of("999");

            ExpandResult result = expander.expand(hitIds, Map.of(), List.of());

            assertThat(result.contextInsights()).isEmpty();
            assertThat(result.expandedLeafs()).isEmpty();
        }
    }
}
