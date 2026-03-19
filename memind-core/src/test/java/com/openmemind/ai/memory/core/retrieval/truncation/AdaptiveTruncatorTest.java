package com.openmemind.ai.memory.core.retrieval.truncation;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AdaptiveTruncator Adaptive Truncation Test")
class AdaptiveTruncatorTest {

    private final TruncationConfig defaults = TruncationConfig.defaults();

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ScoredResult scored(double finalScore) {
        return new ScoredResult(
                ScoredResult.SourceType.ITEM,
                "id-" + finalScore,
                "text",
                (float) finalScore,
                finalScore);
    }

    private static List<ScoredResult> scores(double... values) {
        var list = new ArrayList<ScoredResult>();
        for (double v : values) {
            list.add(scored(v));
        }
        return list;
    }

    // ── Elbow Detection ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Elbow Detection")
    class ElbowDetectionTests {

        @Test
        @DisplayName("Clear Cliff [0.92,0.88,0.85,0.61,0.55] should keep 3 results")
        void shouldTruncateAtClearCliff() {
            var input = scores(0.92, 0.88, 0.85, 0.61, 0.55);
            var result = AdaptiveTruncator.truncate(input, defaults);

            assertThat(result.results()).hasSize(3);
            assertThat(result.originalSize()).isEqualTo(5);
            assertThat(result.triggeredBy()).isEqualTo(TruncationResult.TRIGGER_ELBOW);
        }

        @Test
        @DisplayName("Uniform High Scores [0.90,0.88,0.86,0.84,0.82] should keep all")
        void shouldKeepAllWhenUniformHighScores() {
            var input = scores(0.90, 0.88, 0.86, 0.84, 0.82);
            var result = AdaptiveTruncator.truncate(input, defaults);

            assertThat(result.results()).hasSize(5);
            assertThat(result.triggeredBy()).isEqualTo(TruncationResult.TRIGGER_NONE);
        }

        @Test
        @DisplayName("Only 1 Good Result [0.90,0.45,0.42] should keep 1")
        void shouldKeepOnlyOneGoodResult() {
            var input = scores(0.90, 0.45, 0.42);
            var result = AdaptiveTruncator.truncate(input, defaults);

            assertThat(result.results()).hasSize(1);
            assertThat(result.triggeredBy()).isEqualTo(TruncationResult.TRIGGER_ELBOW);
        }
    }

    // ── Relative Threshold ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Relative Threshold")
    class DropRatioTests {

        @Test
        @DisplayName("Gradual Decline [0.92,0.82,0.73,0.55,0.48] should keep 3")
        void shouldTruncateGradualDecline() {
            var input = scores(0.92, 0.82, 0.73, 0.55, 0.48);
            var result = AdaptiveTruncator.truncate(input, defaults);

            // cutoff = 0.92 * 0.65 = 0.598 → 0.55 < 0.598, keep top 3
            assertThat(result.results()).hasSize(3);
            assertThat(result.originalSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("High topScore [0.95,0.80,0.62,0.60] should keep 2")
        void shouldTruncateWithHighTopScore() {
            var input = scores(0.95, 0.80, 0.62, 0.60);
            var result = AdaptiveTruncator.truncate(input, defaults);

            // cutoff = 0.95 * 0.65 = 0.6175 → 0.62 >= 0.6175, 0.60 < 0.6175
            // elbow gaps: 0.15, 0.18, 0.02 → maxGap=0.18 at index 2 (>= 0.15)
            // elbow=2, drop=3 → truncateAt=2
            assertThat(result.results()).hasSize(2);
        }
    }

    // ── Safety Boundaries ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Safety Boundaries")
    class SafetyTests {

        @Test
        @DisplayName("Empty list should return empty result")
        void shouldHandleEmptyList() {
            var result = AdaptiveTruncator.truncate(List.of(), defaults);

            assertThat(result.results()).isEmpty();
            assertThat(result.originalSize()).isZero();
            assertThat(result.triggeredBy()).isEqualTo(TruncationResult.TRIGGER_NONE);
        }

        @Test
        @DisplayName("Single Element [0.85] should keep 1")
        void shouldKeepSingleElement() {
            var input = scores(0.85);
            var result = AdaptiveTruncator.truncate(input, defaults);

            assertThat(result.results()).hasSize(1);
            assertThat(result.triggeredBy()).isEqualTo(TruncationResult.TRIGGER_NONE);
        }

        @Test
        @DisplayName("Fewer than 3 [0.90,0.50] should keep all")
        void shouldKeepAllWhenBelowMinInputSize() {
            var input = scores(0.90, 0.50);
            var result = AdaptiveTruncator.truncate(input, defaults);

            assertThat(result.results()).hasSize(2);
            assertThat(result.triggeredBy()).isEqualTo(TruncationResult.TRIGGER_NONE);
        }

        @Test
        @DisplayName("All Low Scores [0.30,0.28,0.25] should keep all")
        void shouldKeepAllLowScores() {
            var input = scores(0.30, 0.28, 0.25);
            var result = AdaptiveTruncator.truncate(input, defaults);

            // elbow gaps: 0.02, 0.03 → all < 0.15 → no elbow
            // cutoff = 0.30 * 0.65 = 0.195 → all >= 0.195 → no drop
            assertThat(result.results()).hasSize(3);
            assertThat(result.triggeredBy()).isEqualTo(TruncationResult.TRIGGER_NONE);
        }

        @Test
        @DisplayName("At least keep 1 after truncation [0.95,0.20,0.18]")
        void shouldKeepAtLeastOne() {
            var input = scores(0.95, 0.20, 0.18);
            var result = AdaptiveTruncator.truncate(input, defaults);

            assertThat(result.results()).hasSizeGreaterThanOrEqualTo(1);
        }
    }

    // ── Disable Truncation ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Disable Truncation")
    class DisabledTests {

        @Test
        @DisplayName("disabled config should keep all")
        void shouldKeepAllWhenDisabled() {
            var input = scores(0.92, 0.88, 0.85, 0.61, 0.55);
            var result = AdaptiveTruncator.truncate(input, TruncationConfig.disabled());

            assertThat(result.results()).hasSize(5);
            assertThat(result.triggeredBy()).isEqualTo(TruncationResult.TRIGGER_NONE);
        }
    }

    // ── Custom Parameters ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Custom Parameters")
    class CustomParamsTests {

        @Test
        @DisplayName(
                "Stricter minElbowGap=0.20 [0.92,0.88,0.85,0.68,0.55] should be triggered by"
                        + " drop_ratio")
        void shouldFallbackToDropRatioWithStricterElbow() {
            var config = TruncationConfig.of(0.20, 0.65);
            var input = scores(0.92, 0.88, 0.85, 0.68, 0.55);
            var result = AdaptiveTruncator.truncate(input, config);

            // elbow gaps: 0.04, 0.03, 0.17, 0.13 → maxGap=0.17 < 0.20 → no elbow
            // cutoff = 0.92 * 0.65 = 0.598 → 0.55 < 0.598 → drop at index 4
            assertThat(result.results()).hasSize(4);
            assertThat(result.triggeredBy()).isEqualTo(TruncationResult.TRIGGER_DROP_RATIO);
        }

        @Test
        @DisplayName(
                "Looser dropRatio=0.40 [0.92,0.82,0.73,0.55,0.48] should be triggered by elbow")
        void shouldTriggerElbowWithLooserDropRatio() {
            var config = TruncationConfig.of(0.15, 0.40);
            var input = scores(0.92, 0.82, 0.73, 0.55, 0.48);
            var result = AdaptiveTruncator.truncate(input, config);

            // elbow gaps: 0.10, 0.09, 0.18, 0.07 → maxGap=0.18 at index 3 (>= 0.15)
            // cutoff = 0.92 * 0.40 = 0.368 → all >= 0.368 → no drop
            // truncateAt = min(3, 5) = 3
            assertThat(result.results()).hasSize(3);
            assertThat(result.triggeredBy()).isEqualTo(TruncationResult.TRIGGER_ELBOW);
        }
    }
}
