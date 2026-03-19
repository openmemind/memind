package com.openmemind.ai.memory.core.data.enums;

/**
 * Insight Tree Level
 *
 * <p>Fixed three-layer structure: Leaf → Branch → Root
 */
public enum InsightTier {
    /** Leaf: Directly generated from Items, the finest granularity */
    LEAF,
    /** Branch: Clusters multiple Leaves, medium granularity */
    BRANCH,
    /** Root: Aggregates all Branches, highest compression */
    ROOT
}
