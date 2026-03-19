package com.openmemind.ai.memory.core.retrieval.scoring;

import static com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig.defaults;
import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ResultMerger RRF keyword merge")
class ResultMergerRrfKeywordTest {

    static ScoredResult item(String id, float vecScore, double finalScore) {
        return new ScoredResult(
                ScoredResult.SourceType.ITEM, id, "text-" + id, vecScore, finalScore);
    }

    static TextSearchResult bm25Hit(String id, double score) {
        return new TextSearchResult(id, "text-" + id, score);
    }

    @Nested
    @DisplayName("P1: RRF merge attributes")
    class RrfProperties {

        @Test
        @DisplayName(
                "P1.1: Items that hit both vector and BM25 should rank higher than single-channel"
                        + " hits")
        void dualChannelRanksHigher() {
            var vecList = List.of(item("B", 0.9f, 0.9), item("C", 0.8f, 0.8), item("A", 0.7f, 0.7));
            var bm25List = List.of(bm25Hit("A", 10.0), bm25Hit("D", 5.0));

            List<ScoredResult> merged =
                    ResultMerger.rrfMergeWithKeywords(
                            defaults(), vecList, bm25List, 10, ScoredResult.SourceType.ITEM);

            int rankA = findRank(merged, "A");
            int rankB = findRank(merged, "B");
            assertThat(rankA).isLessThan(rankB);
        }

        @Test
        @DisplayName(
                "P1.2: BM25-only results are not suppressed — scores should be significantly higher"
                        + " than 0.3")
        void bm25OnlyNotSuppressed() {
            var vecList = List.of(item("A", 0.9f, 0.9));
            var bm25List = List.of(bm25Hit("B", 10.0));

            List<ScoredResult> merged =
                    ResultMerger.rrfMergeWithKeywords(
                            defaults(), vecList, bm25List, 10, ScoredResult.SourceType.ITEM);

            ScoredResult bm25Only =
                    merged.stream().filter(r -> r.sourceId().equals("B")).findFirst().orElseThrow();
            assertThat(bm25Only.finalScore()).isGreaterThan(0.3);
        }

        @Test
        @DisplayName("P1.3: RRF monotonicity — higher rank in the same list contributes more")
        void rrfMonotonicity() {
            var vecList = List.of(item("A", 0.9f, 0.9), item("B", 0.8f, 0.8), item("C", 0.7f, 0.7));
            var bm25List = List.<TextSearchResult>of();

            List<ScoredResult> merged =
                    ResultMerger.rrfMergeWithKeywords(
                            defaults(), vecList, bm25List, 10, ScoredResult.SourceType.ITEM);

            assertThat(scoreOf(merged, "A")).isGreaterThan(scoreOf(merged, "B"));
            assertThat(scoreOf(merged, "B")).isGreaterThan(scoreOf(merged, "C"));
        }

        @Test
        @DisplayName(
                "P1.4: When the BM25 list is empty, return vector results directly (maintain"
                        + " original order)")
        void emptyBm25ReturnsVectorResults() {
            var vecList = List.of(item("A", 0.9f, 0.9));
            var bm25List = List.<TextSearchResult>of();

            List<ScoredResult> merged =
                    ResultMerger.rrfMergeWithKeywords(
                            defaults(), vecList, bm25List, 10, ScoredResult.SourceType.ITEM);

            assertThat(merged).hasSize(1);
            assertThat(merged.getFirst().sourceId()).isEqualTo("A");
        }
    }

    private int findRank(List<ScoredResult> results, String id) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).sourceId().equals(id)) return i;
        }
        return Integer.MAX_VALUE;
    }

    private double scoreOf(List<ScoredResult> results, String id) {
        return results.stream()
                .filter(r -> r.sourceId().equals(id))
                .findFirst()
                .map(ScoredResult::finalScore)
                .orElse(0.0);
    }
}
