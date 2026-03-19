package com.openmemind.ai.memory.core.extraction.insight.tree;

/**
 * Insight shared tree configuration
 *
 * <p>Fixed three-layer structure (Leaf → Branch → Root), controlled by bubble threshold for automatic reorganization.
 *
 * @param branchBubbleThreshold LEAF changes how many times before re-summarizing BRANCH
 * @param rootBubbleThreshold   BRANCH changes how many times before re-summarizing ROOT
 * @param minBranchesForRoot    Minimum number of BRANCH required to create ROOT
 * @param rootTargetTokens      Token budget for ROOT re-summarization
 */
public record InsightTreeConfig(
        int branchBubbleThreshold,
        int rootBubbleThreshold,
        int minBranchesForRoot,
        int rootTargetTokens) {

    public static InsightTreeConfig defaults() {
        return new InsightTreeConfig(3, 2, 2, 800);
    }
}
