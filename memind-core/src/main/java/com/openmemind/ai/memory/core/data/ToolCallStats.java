package com.openmemind.ai.memory.core.data;

/**
 * Tool call statistics aggregation (pure computation, not LLM)
 *
 */
public record ToolCallStats(
        int totalCalls,
        int recentCallsAnalyzed,
        double successRate,
        double avgTimeCost,
        double avgScore,
        double avgTokenCost) {}
