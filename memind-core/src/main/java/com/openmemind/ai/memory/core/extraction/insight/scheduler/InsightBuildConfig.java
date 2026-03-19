package com.openmemind.ai.memory.core.extraction.insight.scheduler;

/**
 * InsightBuildScheduler Configuration
 *
 * @param groupingThreshold  The threshold for the number of ungrouped items to trigger grouping
 * @param buildThreshold     The threshold for the number of unbuilt items within a group to trigger leaf building
 * @param concurrency        The maximum number of concurrent insight types
 * @param maxRetries         The number of retry attempts on failure
 */
public record InsightBuildConfig(
        int groupingThreshold, int buildThreshold, int concurrency, int maxRetries) {

    public static InsightBuildConfig defaults() {
        return new InsightBuildConfig(20, 10, 4, 2);
    }
}
