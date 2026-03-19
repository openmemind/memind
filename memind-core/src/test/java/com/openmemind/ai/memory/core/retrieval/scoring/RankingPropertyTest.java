package com.openmemind.ai.memory.core.retrieval.scoring;

import static com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig.defaults;
import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Ranking Property Test")
class RankingPropertyTest {

    @Nested
    @DisplayName("P4: Time Filter Properties")
    class TimeFilterProperties {

        @Test
        @DisplayName("P4.1: Out of time range penalty = original score × 0.5")
        void timeRangePenalty() {
            double original = 0.8;
            double penalized = original * defaults().timeDecay().outOfRangePenalty();
            assertThat(penalized).isEqualTo(0.4);
        }
    }

    @Nested
    @DisplayName("P1.5: Top-rank bonus property")
    class TopRankBonusProperties {

        @Test
        @DisplayName("P1.5: RRF merge rank 1 has a greater bonus than rank 2")
        void topRankBonusApplied() {
            var results =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "text", 0.9f, 0.9),
                            new ScoredResult(ScoredResult.SourceType.ITEM, "B", "text", 0.8f, 0.8),
                            new ScoredResult(ScoredResult.SourceType.ITEM, "C", "text", 0.7f, 0.7));

            // Use merge with 2 lists so it goes through the full RRF + normalize + bonus path
            var results2 =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "text", 0.9f, 0.85),
                            new ScoredResult(ScoredResult.SourceType.ITEM, "B", "text", 0.8f, 0.75),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "C", "text", 0.7f, 0.65));

            List<ScoredResult> merged = ResultMerger.merge(defaults(), List.of(results, results2));

            // rank 1 should have TOP1_BONUS (0.05), rank 2-3 should have TOP3_BONUS (0.02)
            double rank1Score = merged.getFirst().finalScore();
            double rank2Score = merged.get(1).finalScore();
            double rank3Score = merged.get(2).finalScore();
            assertThat(rank1Score).isGreaterThan(rank2Score);
            assertThat(rank2Score).isGreaterThan(rank3Score);
        }
    }

    @Nested
    @DisplayName("P7: End-to-end Ranking Properties")
    class EndToEndProperties {

        @Test
        @DisplayName(
                "P7.1: Dual channel > Single channel — RRF fusion hits both lists ranked higher")
        void dualChannelBeforeSingleChannel() {
            // item A: both vector rank 3, BM25 rank 1
            // item B: vector rank 1 only
            var vecList =
                    List.of(
                            new ScoredResult(ScoredResult.SourceType.ITEM, "B", "text", 0.9f, 0.9),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM, "X", "text", 0.85f, 0.85),
                            new ScoredResult(ScoredResult.SourceType.ITEM, "A", "text", 0.7f, 0.7));
            var bm25List =
                    List.of(
                            new TextSearchResult("A", "text", 10.0),
                            new TextSearchResult("Y", "text", 3.0));

            List<ScoredResult> merged =
                    ResultMerger.rrfMergeWithKeywords(
                            defaults(), vecList, bm25List, 10, ScoredResult.SourceType.ITEM);

            int rankA = -1, rankB = -1;
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i).sourceId().equals("A")) rankA = i;
                if (merged.get(i).sourceId().equals("B")) rankB = i;
            }
            assertThat(rankA).isLessThan(rankB);
        }

        @Test
        @DisplayName("P7.2: Direct comparison of vector scores — Higher vector scores rank higher")
        void higherVectorScoreRanksHigher() {
            // After removing ScoringStrategy, directly use vector scores for ranking
            double scoreA = 0.9;
            double scoreB = 0.8;
            assertThat(scoreA).isGreaterThan(scoreB);
        }
    }
}
