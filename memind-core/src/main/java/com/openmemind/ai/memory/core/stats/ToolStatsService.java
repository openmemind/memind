package com.openmemind.ai.memory.core.stats;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.ToolCallStats;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Tool call statistics service
 *
 */
public interface ToolStatsService {

    Mono<ToolCallStats> getToolStats(MemoryId memoryId, String toolName);

    Mono<Map<String, ToolCallStats>> getAllToolStats(MemoryId memoryId);
}
