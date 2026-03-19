package com.openmemind.ai.memory.core.retrieval.scoring;

import static com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig.defaults;
import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Strong Signal Detection")
class StrongSignalTest {

    @Nested
    @DisplayName("P5: Strong Signal Properties")
    class StrongSignalProperties {

        @Test
        @DisplayName("P5.1: Strong Signal — top sigmoid ≥ 0.85 and gap ≥ 0.15")
        void strongSignalDetected() {
            // score=10 → sigmoid≈0.91, score=1 → sigmoid≈0.5, gap=0.41
            var results =
                    List.of(
                            new TextSearchResult("A", "text", 10.0),
                            new TextSearchResult("B", "text", 1.0));
            assertThat(ResultMerger.isStrongSignal(defaults(), results)).isTrue();
        }

        @Test
        @DisplayName("P5.2: Weak Signal — top sigmoid < 0.85")
        void weakSignalNotDetected() {
            // score=2 → sigmoid≈0.67, score=1.5 → sigmoid≈0.6
            var results =
                    List.of(
                            new TextSearchResult("A", "text", 2.0),
                            new TextSearchResult("B", "text", 1.5));
            assertThat(ResultMerger.isStrongSignal(defaults(), results)).isFalse();
        }

        @Test
        @DisplayName("P5.3: Single result does not trigger")
        void singleResultNotStrong() {
            var results = List.of(new TextSearchResult("A", "text", 10.0));
            assertThat(ResultMerger.isStrongSignal(defaults(), results)).isFalse();
        }

        @Test
        @DisplayName("P5.4: Empty result does not trigger")
        void emptyResultNotStrong() {
            assertThat(ResultMerger.isStrongSignal(defaults(), List.of())).isFalse();
        }
    }
}
