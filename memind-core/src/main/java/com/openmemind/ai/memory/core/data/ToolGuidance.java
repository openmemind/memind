package com.openmemind.ai.memory.core.data;

import java.util.List;
import java.util.Map;

/**
 * Tool usage suggestion retrieval results
 *
 */
public record ToolGuidance(
        List<String> insights, List<String> itemSummaries, Map<String, ToolCallStats> stats) {}
