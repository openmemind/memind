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
package com.openmemind.ai.memory.core.retrieval.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.openmemind.ai.memory.core.llm.rerank.LlmReranker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LlmReranker Unit Test")
class LlmRerankerTest {

    private final LlmReranker reranker = new LlmReranker("http://localhost:8080", "test-key");

    @Nested
    @DisplayName("blendScore - Position Aware Blending Score")
    class BlendScoreTests {

        @Test
        @DisplayName(
                "Rank 1: Should use Top3 weight (0.7), blended score = 0.7 * 1.0 + 0.3 *"
                        + " rerankerScore")
        void shouldApplyTop3WeightForRank1() {
            double rerankerScore = 0.85;
            double expected = 0.7 * 1.0 + (1.0 - 0.7) * rerankerScore;

            double result = reranker.legacyBlendScore(1, rerankerScore);

            assertThat(result).isCloseTo(expected, within(1e-9));
            // 0.7 * 1.0 + 0.3 * 0.85 = 0.7 + 0.255 = 0.955
            assertThat(result).isCloseTo(0.955, within(1e-9));
        }

        @Test
        @DisplayName(
                "Rank 2: Should use Top3 weight (0.7), blended score = 0.7 * 0.5 + 0.3 *"
                        + " rerankerScore")
        void shouldApplyTop3WeightForRank2() {
            double rerankerScore = 0.9;
            double expected = 0.7 * (1.0 / 2) + (1.0 - 0.7) * rerankerScore;

            double result = reranker.legacyBlendScore(2, rerankerScore);

            assertThat(result).isCloseTo(expected, within(1e-9));
            // 0.7 * 0.5 + 0.3 * 0.9 = 0.35 + 0.27 = 0.62
            assertThat(result).isCloseTo(0.62, within(1e-9));
        }

        @Test
        @DisplayName("Rank 3: Should use Top3 weight (0.7) boundary value")
        void shouldApplyTop3WeightForRank3() {
            double rerankerScore = 0.8;
            double expected = 0.7 * (1.0 / 3) + (1.0 - 0.7) * rerankerScore;

            double result = reranker.legacyBlendScore(3, rerankerScore);

            assertThat(result).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName(
                "Rank 5: Should use Top10 weight (0.5), blended score = 0.5 * 0.2 + 0.5 *"
                        + " rerankerScore")
        void shouldApplyTop10WeightForRank5() {
            double rerankerScore = 0.75;
            double expected = 0.5 * (1.0 / 5) + (1.0 - 0.5) * rerankerScore;

            double result = reranker.legacyBlendScore(5, rerankerScore);

            assertThat(result).isCloseTo(expected, within(1e-9));
            // 0.5 * 0.2 + 0.5 * 0.75 = 0.1 + 0.375 = 0.475
            assertThat(result).isCloseTo(0.475, within(1e-9));
        }

        @Test
        @DisplayName("Rank 4: Should use Top10 weight (0.5) lower bound")
        void shouldApplyTop10WeightForRank4() {
            double rerankerScore = 0.6;
            double expected = 0.5 * (1.0 / 4) + (1.0 - 0.5) * rerankerScore;

            double result = reranker.legacyBlendScore(4, rerankerScore);

            assertThat(result).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("Rank 10: Should use Top10 weight (0.5) upper bound")
        void shouldApplyTop10WeightForRank10() {
            double rerankerScore = 0.5;
            double expected = 0.5 * (1.0 / 10) + (1.0 - 0.5) * rerankerScore;

            double result = reranker.legacyBlendScore(10, rerankerScore);

            assertThat(result).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName(
                "Rank 15: Should use default weight (0.3), blended score = 0.3 * (1/15) + 0.7 *"
                        + " rerankerScore")
        void shouldApplyOtherWeightForRank15() {
            double rerankerScore = 0.6;
            double expected = 0.3 * (1.0 / 15) + (1.0 - 0.3) * rerankerScore;

            double result = reranker.legacyBlendScore(15, rerankerScore);

            assertThat(result).isCloseTo(expected, within(1e-9));
            // 0.3 * (1/15) + 0.7 * 0.6 = 0.02 + 0.42 = 0.44
            assertThat(result).isCloseTo(0.44, within(1e-9));
        }

        @Test
        @DisplayName("Rank 11: Should use default weight (0.3) lower bound")
        void shouldApplyOtherWeightForRank11() {
            double rerankerScore = 0.9;
            double expected = 0.3 * (1.0 / 11) + (1.0 - 0.3) * rerankerScore;

            double result = reranker.legacyBlendScore(11, rerankerScore);

            assertThat(result).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName(
                "When rerankerScore is 0, blended score should be determined only by position"
                        + " weight")
        void shouldHandleZeroRerankerScore() {
            double result = reranker.legacyBlendScore(1, 0.0);

            // 0.7 * 1.0 + 0.3 * 0.0 = 0.7
            assertThat(result).isCloseTo(0.7, within(1e-9));
        }

        @Test
        @DisplayName("When rerankerScore is 1.0, should receive the highest blended score")
        void shouldHandleMaxRerankerScore() {
            double result = reranker.legacyBlendScore(1, 1.0);

            // 0.7 * 1.0 + 0.3 * 1.0 = 1.0
            assertThat(result).isCloseTo(1.0, within(1e-9));
        }
    }

    @Nested
    @DisplayName("blendScore - Sorting Impact Verification")
    class BlendScoreOrderingTests {

        @Test
        @DisplayName(
                "High rank low semantic score vs low rank high semantic score: blending should"
                        + " reflect positional advantage")
        void highRankLowSemanticVsLowRankHighSemantic() {
            // Rank 1 result, reranker gave a medium score
            double rank1Score = reranker.legacyBlendScore(1, 0.5);
            // Rank 15 result, reranker gave a high score
            double rank15Score = reranker.legacyBlendScore(15, 0.95);

            // rank1: 0.7 * 1.0 + 0.3 * 0.5 = 0.85
            // rank15: 0.3 * (1/15) + 0.7 * 0.95 = 0.02 + 0.665 = 0.685
            assertThat(rank1Score).isGreaterThan(rank15Score);
        }

        @Test
        @DisplayName("Within the same rank tier: higher reranker score should rank higher")
        void withinSameRankTierHigherRerankerWins() {
            double score1 = reranker.legacyBlendScore(5, 0.9);
            double score2 = reranker.legacyBlendScore(5, 0.3);

            assertThat(score1).isGreaterThan(score2);
        }

        @Test
        @DisplayName(
                "Boundary between adjacent rank tiers: Rank 3 vs Rank 4 weight switch verification")
        void boundaryBetweenTop3AndTop10() {
            double rank3Score = reranker.legacyBlendScore(3, 0.7);
            double rank4Score = reranker.legacyBlendScore(4, 0.7);

            // rank3: 0.7 * (1/3) + 0.3 * 0.7 ≈ 0.2333 + 0.21 = 0.4433
            // rank4: 0.5 * (1/4) + 0.5 * 0.7 = 0.125 + 0.35 = 0.475
            // rank4 may be higher due to weight change -- this verifies the formula behavior
            assertThat(rank3Score).isNotEqualTo(rank4Score);
        }

        @Test
        @DisplayName(
                "Boundary between adjacent rank tiers: Rank 10 vs Rank 11 weight switch"
                        + " verification")
        void boundaryBetweenTop10AndOther() {
            double rank10Score = reranker.legacyBlendScore(10, 0.7);
            double rank11Score = reranker.legacyBlendScore(11, 0.7);

            // rank10: 0.5 * (1/10) + 0.5 * 0.7 = 0.05 + 0.35 = 0.4
            // rank11: 0.3 * (1/11) + 0.7 * 0.7 ≈ 0.0273 + 0.49 = 0.5173
            // rank11 gets more reranker weight, which dominates when rerankerScore is high
            assertThat(rank10Score).isNotEqualTo(rank11Score);
        }
    }
}
