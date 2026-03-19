package com.openmemind.ai.memory.core.retrieval.truncation;

import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adaptive Truncator -- Two-level truncation algorithm based on elbow detection + relative threshold
 *
 * <p>Algorithm process:
 * <ol>
 *   <li>L1 Elbow detection: Find the maximum gap between adjacent scores, if gap >= minElbowGap then truncate at that position</li>
 *   <li>L2 Relative threshold: Use topScore * dropRatio as the score line, results below this line are removed</li>
 *   <li>Take the more aggressive (smaller index) truncation point of the two, ensuring at least 1 result is retained</li>
 * </ol>
 */
public final class AdaptiveTruncator {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveTruncator.class);

    /** Skip truncation when the input list is less than this number */
    private static final int MIN_INPUT_SIZE = 3;

    /** Minimum number of results to retain after truncation */
    private static final int MIN_RESULT_COUNT = 1;

    private AdaptiveTruncator() {}

    /**
     * Perform adaptive truncation on the retrieval results
     *
     * @param results The list of scored results to be truncated
     * @param config  Truncation configuration
     * @return Truncation result
     */
    public static TruncationResult truncate(List<ScoredResult> results, TruncationConfig config) {
        if (!config.enabled() || results.size() < MIN_INPUT_SIZE) {
            return TruncationResult.unchanged(results);
        }

        // Sort by finalScore in descending order
        var sorted =
                results.stream()
                        .sorted(Comparator.comparingDouble(ScoredResult::finalScore).reversed())
                        .toList();

        double topScore = sorted.getFirst().finalScore();

        // L1: Elbow detection
        int elbowIndex = findElbow(sorted, config.minElbowGap());

        // L2: Relative threshold
        int dropIndex = findDropRatioIndex(sorted, topScore, config.dropRatio());

        // Take the more aggressive truncation point
        int truncateAt = Math.min(elbowIndex, dropIndex);

        // Safety lower limit
        truncateAt = Math.max(truncateAt, MIN_RESULT_COUNT);

        if (truncateAt >= sorted.size()) {
            return TruncationResult.unchanged(sorted);
        }

        String triggeredBy =
                elbowIndex <= dropIndex
                        ? TruncationResult.TRIGGER_ELBOW
                        : TruncationResult.TRIGGER_DROP_RATIO;

        var truncated = sorted.subList(0, truncateAt);
        double cutoffScore = truncated.getLast().finalScore();

        log.debug(
                "Adaptive truncation: {} → {} items, triggeredBy={}, cutoff={}, topScore={}",
                results.size(),
                truncated.size(),
                triggeredBy,
                cutoffScore,
                topScore);

        return new TruncationResult(truncated, results.size(), cutoffScore, triggeredBy);
    }

    /**
     * Elbow detection: Find the maximum gap between adjacent scores
     *
     * @return Truncation position index (retain elements in [0, index)), if no significant elbow then return sorted.size()
     */
    static int findElbow(List<ScoredResult> sorted, double minElbowGap) {
        double maxGap = 0.0;
        int elbowIndex = sorted.size();

        for (int i = 0; i < sorted.size() - 1; i++) {
            double gap = sorted.get(i).finalScore() - sorted.get(i + 1).finalScore();
            if (gap > maxGap) {
                maxGap = gap;
                elbowIndex = i + 1;
            }
        }

        return maxGap >= minElbowGap ? elbowIndex : sorted.size();
    }

    /**
     * Relative threshold detection: Find the position of the first element below topScore * dropRatio
     *
     * @return Truncation position index (retain elements in [0, index)), if all pass then return sorted.size()
     */
    static int findDropRatioIndex(List<ScoredResult> sorted, double topScore, double dropRatio) {
        double cutoff = topScore * dropRatio;

        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).finalScore() < cutoff) {
                return i;
            }
        }

        return sorted.size();
    }
}
