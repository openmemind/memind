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
package com.openmemind.ai.memory.core.extraction.insight.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.InsightPointRef;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InsightPointEvidenceNormalizerTest {

    private final InsightPointEvidenceNormalizer normalizer = new InsightPointEvidenceNormalizer();

    @Test
    void normalizeLeafPointsShouldSortDistinctSourceItemIdsAndClearPointRefs() {
        var normalized =
                normalizer.normalizeLeafPoints(
                        List.of(
                                new InsightPoint(
                                        null,
                                        InsightPoint.PointType.SUMMARY,
                                        "Leaf",
                                        List.of("3", "1", "3", "2"),
                                        List.of(new InsightPointRef(10L, "pt_branch")),
                                        null)));

        assertThat(normalized.getFirst().sourceItemIds()).containsExactly("1", "2", "3");
        assertThat(normalized.getFirst().sourcePointRefs()).isEmpty();
    }

    @Test
    void normalizeBranchPointsShouldKeepValidatedLeafRefsOnly() {
        var leaf =
                insight(
                        10L,
                        InsightTier.LEAF,
                        List.of(
                                new InsightPoint(
                                        "pt_leaf_1",
                                        InsightPoint.PointType.SUMMARY,
                                        "Leaf point",
                                        List.of("1"))));

        var normalized =
                normalizer.normalizeBranchPoints(
                        List.of(
                                new InsightPoint(
                                        null,
                                        InsightPoint.PointType.REASONING,
                                        "Branch point",
                                        List.of("ignored"),
                                        List.of(new InsightPointRef(10L, "pt_leaf_1")),
                                        Map.of("dimension", "convergence"))),
                        List.of(leaf));

        assertThat(normalized.getFirst().sourceItemIds()).isEmpty();
        assertThat(normalized.getFirst().sourcePointRefs())
                .containsExactly(new InsightPointRef(10L, "pt_leaf_1"));
    }

    @Test
    void normalizeRootPointsShouldDropDanglingBranchRefs() {
        var branch =
                insight(
                        20L,
                        InsightTier.BRANCH,
                        List.of(
                                new InsightPoint(
                                        "pt_branch_1",
                                        InsightPoint.PointType.SUMMARY,
                                        "Branch point",
                                        List.of())));

        var normalized =
                normalizer.normalizeRootPoints(
                        List.of(
                                new InsightPoint(
                                        null,
                                        InsightPoint.PointType.REASONING,
                                        "Root point",
                                        List.of(),
                                        List.of(new InsightPointRef(20L, "pt_missing")),
                                        null)),
                        List.of(branch));

        assertThat(normalized).hasSize(1);
        assertThat(normalized.getFirst().content()).isEqualTo("Root point");
        assertThat(normalized.getFirst().sourcePointRefs()).isEmpty();
    }

    @Test
    void normalizeBranchPointsShouldKeepValidRefsWhenMixedWithDanglingRefs() {
        var leaf =
                insight(
                        10L,
                        InsightTier.LEAF,
                        List.of(
                                new InsightPoint(
                                        "pt_leaf_1",
                                        InsightPoint.PointType.SUMMARY,
                                        "Leaf point",
                                        List.of("1"))));

        var normalized =
                normalizer.normalizeBranchPoints(
                        List.of(
                                new InsightPoint(
                                        null,
                                        InsightPoint.PointType.REASONING,
                                        "Branch point",
                                        List.of(),
                                        List.of(
                                                new InsightPointRef(10L, "pt_missing"),
                                                new InsightPointRef(10L, "pt_leaf_1"),
                                                new InsightPointRef(99L, "pt_unknown")),
                                        null)),
                        List.of(leaf));

        assertThat(normalized.getFirst().sourcePointRefs())
                .containsExactly(new InsightPointRef(10L, "pt_leaf_1"));
    }

    @Test
    void normalizeBranchPointsShouldTreatNullTierAsLegacyLeafEvidence() {
        var legacyLeaf =
                insight(
                        30L,
                        null,
                        List.of(
                                new InsightPoint(
                                        "pt_leaf_legacy",
                                        InsightPoint.PointType.SUMMARY,
                                        "Legacy leaf",
                                        List.of("7"))));

        var normalized =
                normalizer.normalizeBranchPoints(
                        List.of(
                                new InsightPoint(
                                        null,
                                        InsightPoint.PointType.SUMMARY,
                                        "Branch from legacy leaf",
                                        List.of(),
                                        List.of(new InsightPointRef(30L, "pt_leaf_legacy")),
                                        null)),
                        List.of(legacyLeaf));

        assertThat(normalized.getFirst().sourcePointRefs())
                .containsExactly(new InsightPointRef(30L, "pt_leaf_legacy"));
    }

    private static MemoryInsight insight(Long id, InsightTier tier, List<InsightPoint> points) {
        Instant now = Instant.parse("2026-04-15T00:00:00Z");
        return new MemoryInsight(
                id,
                "memory-1",
                "identity",
                MemoryScope.USER,
                "insight-" + id,
                List.of("profile"),
                points,
                null,
                now,
                null,
                now,
                now,
                tier,
                null,
                List.of(),
                1);
    }
}
