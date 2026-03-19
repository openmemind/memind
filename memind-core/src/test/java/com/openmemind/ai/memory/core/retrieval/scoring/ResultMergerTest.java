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
package com.openmemind.ai.memory.core.retrieval.scoring;

import static com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig.defaults;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ResultMerger Unit Test")
class ResultMergerTest {

    @Nested
    @DisplayName("RRF Merge")
    class MergeTests {

        @Test
        @DisplayName("Empty list should return empty result")
        void shouldReturnEmptyForEmptyInput() {
            List<ScoredResult> result = ResultMerger.merge(defaults(), List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Single list should return as is")
        void shouldReturnSingleListAsIs() {
            var list =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "1", "text1", 0.9f, 0.9),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "2", "text2", 0.8f, 0.8));

            List<ScoredResult> result = ResultMerger.merge(defaults(), List.of(list));
            assertThat(result).hasSize(2);
            assertThat(result.getFirst().sourceId()).isEqualTo("1");
        }

        @Test
        @DisplayName("Multiple lists should be merged and sorted by RRF score")
        void shouldMergeByRrfScore() {
            var list1 =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "textA", 0.9f, 0.9),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "B", "textB", 0.8f, 0.8));
            var list2 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "B", "textB", 0.85f, 0.85),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "A", "textA", 0.7f, 0.7));

            List<ScoredResult> result = ResultMerger.merge(defaults(), List.of(list1, list2));

            // A and B both appear in two lists
            // A: rank 0 in list1 (1/61) + rank 1 in list2 (1/62) = 0.01639 + 0.01613 = 0.03252
            // B: rank 1 in list1 (1/62) + rank 0 in list2 (1/61) = 0.01613 + 0.01639 = 0.03252
            // Scores are the same, but B's vectorScore is higher (0.85 > 0.7)
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Deduplication should keep the highest vectorScore")
        void shouldKeepHighestVectorScore() {
            var list1 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "X", "textX", 0.6f, 0.6));
            var list2 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "X", "textX", 0.9f, 0.9));

            List<ScoredResult> result = ResultMerger.merge(defaults(), List.of(list1, list2));

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().vectorScore()).isEqualTo(0.9f);
        }

        @Test
        @DisplayName("Different sourceType with the same sourceId should not be deduplicated")
        void shouldNotDedupDifferentSourceTypes() {
            var list1 =
                    List.of(new ScoredResult(ScoredResult.SourceType.ITEM, "1", "item", 0.9f, 0.9));
            var list2 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.INSIGHT, "1", "insight", 0.8f, 0.8));

            List<ScoredResult> result = ResultMerger.merge(defaults(), List.of(list1, list2));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Scores after merging should be normalized and have top-rank bonus added")
        void shouldNormalizeScoresToZeroOneRange() {
            var list1 =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "textA", 0.9f, 0.9),
                            new ScoredResult(ScoredResult.SourceType.ITEM, "B", "textB", 0.8f, 0.8),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "C", "textC", 0.7f, 0.7));
            var list2 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "A", "textA", 0.85f, 0.85));

            List<ScoredResult> result = ResultMerger.merge(defaults(), List.of(list1, list2));

            // Highest score = normalized 1.0 + TOP1_BONUS 0.05, lowest score should be > 0
            assertThat(result.getFirst().finalScore()).isEqualTo(1.05);
            assertThat(result.getLast().finalScore()).isGreaterThan(0.0);

            // All scores should be > 0 (top-rank bonus can make the highest score slightly exceed
            // 1.0)
            result.forEach(
                    r -> {
                        assertThat(r.finalScore()).isGreaterThan(0.0);
                    });
        }

        @Test
        @DisplayName("Items appearing in more lists should receive higher RRF scores")
        void shouldBoostItemsAppearingInMoreLists() {
            var list1 =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "textA", 0.9f, 0.9),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "B", "textB", 0.8f, 0.8));
            var list2 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "A", "textA", 0.85f, 0.85),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "C", "textC", 0.7f, 0.7));
            var list3 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "A", "textA", 0.8f, 0.8));

            List<ScoredResult> result =
                    ResultMerger.merge(defaults(), List.of(list1, list2, list3));

            // A appears in 3 lists, should rank first
            assertThat(result.getFirst().sourceId()).isEqualTo("A");
        }
    }

    @Nested
    @DisplayName("Relative Score Fusion Merge")
    class MergeByRelativeScoreTests {

        @Test
        @DisplayName("Empty list should return empty result")
        void shouldReturnEmptyForEmptyInput() {
            List<ScoredResult> result = ResultMerger.mergeByRelativeScore(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Single list should return as is")
        void shouldReturnSingleListAsIs() {
            var list =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "1", "text1", 0.9f, 0.9),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "2", "text2", 0.8f, 0.8));

            List<ScoredResult> result = ResultMerger.mergeByRelativeScore(List.of(list));
            assertThat(result).hasSize(2);
            assertThat(result.getFirst().sourceId()).isEqualTo("1");
        }

        @Test
        @DisplayName("High-scoring documents should still receive high weight even if ranked lower")
        void shouldPreserveScoreInformation() {
            // list1: A=0.9, B=0.1 (A much higher than B)
            var list1 =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "textA", 0.9f, 0.9),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "B", "textB", 0.1f, 0.1));
            // list2: B=0.95, C=0.5
            var list2 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "B", "textB", 0.95f, 0.95),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "C", "textC", 0.5f, 0.5));

            List<ScoredResult> result = ResultMerger.mergeByRelativeScore(List.of(list1, list2));

            // B scores low in list1 (0.1/0.9) but scores high in list2 (0.95/0.95=1.0)
            // RSF preserves score information, B should still receive a high total score
            assertThat(result).hasSize(3);
            result.forEach(
                    r -> assertThat(r.finalScore()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0));
        }

        @Test
        @DisplayName("Items appearing in multiple lists should accumulate scores")
        void shouldAccumulateScoresAcrossLists() {
            var list1 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "A", "textA", 0.9f, 0.9));
            var list2 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "A", "textA", 0.85f, 0.85),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "B", "textB", 0.8f, 0.8));

            List<ScoredResult> result = ResultMerger.mergeByRelativeScore(List.of(list1, list2));

            // A appears in two lists, accumulated score should be highest
            assertThat(result.getFirst().sourceId()).isEqualTo("A");
            assertThat(result.getFirst().finalScore()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Deduplication should keep the highest vectorScore")
        void shouldKeepHighestVectorScore() {
            var list1 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "X", "textX", 0.6f, 0.6));
            var list2 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "X", "textX", 0.9f, 0.9));

            List<ScoredResult> result = ResultMerger.mergeByRelativeScore(List.of(list1, list2));

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().vectorScore()).isEqualTo(0.9f);
        }

        @Test
        @DisplayName("Scores after merging should be normalized to [0, 1] range")
        void shouldNormalizeScoresToZeroOneRange() {
            var list1 =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "textA", 0.9f, 0.9),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "B", "textB", 0.8f, 0.8));
            var list2 =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "C", "textC", 0.7f, 0.7),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "A", "textA", 0.85f, 0.85));

            List<ScoredResult> result = ResultMerger.mergeByRelativeScore(List.of(list1, list2));

            assertThat(result.getFirst().finalScore()).isEqualTo(1.0);
            result.forEach(
                    r -> assertThat(r.finalScore()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0));
        }
    }

    @Nested
    @DisplayName("BM25/Vector Deduplication Preference")
    class VectorScoreDedupTests {

        @Test
        @DisplayName(
                "Merging should prioritize results with real vectorScore (not BM25's zero score"
                        + " version)")
        void shouldPreferRealVectorScoreOverBm25Zero() {
            // BM25 result: vectorScore=0 (from BM25 channel)
            var bm25List =
                    List.of(new ScoredResult(ScoredResult.SourceType.ITEM, "X", "textX", 0f, 0.85));
            // Vector result: vectorScore=0.3 (from vector channel)
            var vectorList =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "X", "textX", 0.3f, 0.7));

            List<ScoredResult> result =
                    ResultMerger.merge(defaults(), List.of(bm25List, vectorList));

            assertThat(result).hasSize(1);
            // Should keep the version with vectorScore=0.3, not the BM25's 0 score version
            assertThat(result.getFirst().vectorScore()).isEqualTo(0.3f);
        }

        @Test
        @DisplayName("RSF merging should also prioritize results with real vectorScore")
        void shouldPreferRealVectorScoreOverBm25ZeroInRsf() {
            var bm25List =
                    List.of(new ScoredResult(ScoredResult.SourceType.ITEM, "Y", "textY", 0f, 0.9));
            var vectorList =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "Y", "textY", 0.5f, 0.6));

            List<ScoredResult> result =
                    ResultMerger.mergeByRelativeScore(List.of(bm25List, vectorList));

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().vectorScore()).isEqualTo(0.5f);
        }
    }

    @Nested
    @DisplayName("Weighted RRF Merge")
    class WeightedMergeTests {

        @Test
        @DisplayName("Dual hits should rank higher than single hits")
        void shouldBoostDualHitOverSingleHit() {
            // Vector result: A(rank0), B(rank1)
            var vectorResults =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "textA", 0.9f, 0.9),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "B", "textB", 0.8f, 0.8));
            // BM25 result: A(rank0), C(rank1) — C only BM25 hit
            var keywordResults =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "textA", 0f, 0.0),
                            new ScoredResult(ScoredResult.SourceType.ITEM, "C", "textC", 0f, 0.0));

            List<ScoredResult> result =
                    ResultMerger.merge(
                            defaults(), List.of(vectorResults, keywordResults), 1.0, 0.3);

            // A dual hit, should rank first
            assertThat(result.getFirst().sourceId()).isEqualTo("A");
            // A's vectorScore should remain the same
            assertThat(result.getFirst().vectorScore()).isEqualTo(0.9f);
        }

        @Test
        @DisplayName(
                "Low weight BM25-only results' RRF scores should be much lower than vector results")
        void shouldPenalizeBm25OnlyResults() {
            // Vector result: A(rank0)
            var vectorResults =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "A", "textA", 0.9f, 0.9));
            // BM25 result: B(rank0) — only BM25 hit
            var keywordResults =
                    List.of(new ScoredResult(ScoredResult.SourceType.ITEM, "B", "textB", 0f, 0.0));

            List<ScoredResult> result =
                    ResultMerger.merge(
                            defaults(), List.of(vectorResults, keywordResults), 1.0, 0.3);

            // A should rank first (vector weight 1.0), B last (BM25 weight 0.3)
            assertThat(result.getFirst().sourceId()).isEqualTo("A");
            assertThat(result.getLast().sourceId()).isEqualTo("B");
            // B's vectorScore should be 0 (no vector match)
            assertThat(result.getLast().vectorScore()).isEqualTo(0f);
        }

        @Test
        @DisplayName("Not providing weights should be equivalent to equal weight RRF")
        void shouldDefaultToEqualWeightsWhenNoneProvided() {
            var list1 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "A", "textA", 0.9f, 0.9));
            var list2 =
                    List.of(
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "B", "textB", 0.8f, 0.8));

            // Not providing weights
            List<ScoredResult> resultNoWeights =
                    ResultMerger.merge(defaults(), List.of(list1, list2));
            // Providing equal weights
            List<ScoredResult> resultEqualWeights =
                    ResultMerger.merge(defaults(), List.of(list1, list2), 1.0, 1.0);

            // Both results should be consistent
            assertThat(resultNoWeights).hasSize(resultEqualWeights.size());
            for (int i = 0; i < resultNoWeights.size(); i++) {
                assertThat(resultNoWeights.get(i).sourceId())
                        .isEqualTo(resultEqualWeights.get(i).sourceId());
                assertThat(resultNoWeights.get(i).finalScore())
                        .isEqualTo(resultEqualWeights.get(i).finalScore());
            }
        }

        @Test
        @DisplayName("Weighted scores should be normalized and have top-rank bonus added")
        void shouldNormalizeWeightedScores() {
            var vectorResults =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "textA", 0.9f, 0.9),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "B", "textB", 0.8f, 0.8));
            var keywordResults =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "C", "textC", 0f, 0.0),
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "textA", 0f, 0.0));

            List<ScoredResult> result =
                    ResultMerger.merge(
                            defaults(), List.of(vectorResults, keywordResults), 1.0, 0.3);

            result.forEach(r -> assertThat(r.finalScore()).isGreaterThan(0.0));
            // Highest score = normalized 1.0 + TOP1_BONUS 0.05
            assertThat(result.getFirst().finalScore()).isEqualTo(1.05);
        }
    }
}
