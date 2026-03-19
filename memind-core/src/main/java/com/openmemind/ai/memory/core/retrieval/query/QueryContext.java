package com.openmemind.ai.memory.core.retrieval.query;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Query Context
 *
 * <p>Passed between layers during retrieval, contains original query, rewritten query, and contextual information such as conversation history
 *
 * @param memoryId        Memory identifier
 * @param originalQuery   User's original query
 * @param rewrittenQuery  Rewritten query (resolving pronouns, supplementing context)
 * @param conversationHistory Recent conversation history (used for LLM intent judgment and query rewriting)
 * @param metadata        Additional metadata
 * @param scope           Scope filter (null means no filter)
 * @param categories      Category filter (null means no filter)
 */
public record QueryContext(
        MemoryId memoryId,
        String originalQuery,
        String rewrittenQuery,
        List<String> conversationHistory,
        Map<String, Object> metadata,
        MemoryScope scope,
        Set<MemoryCategory> categories) {

    /** metadata key: Time range start (Instant) */
    public static final String META_TIME_RANGE_START = "timeRangeStart";

    /** metadata key: Time range end (Instant) */
    public static final String META_TIME_RANGE_END = "timeRangeEnd";

    /** Get the query text for vector search (prefer using the rewritten one) */
    public String searchQuery() {
        return rewrittenQuery != null && !rewrittenQuery.isBlank() ? rewrittenQuery : originalQuery;
    }

    /** Get the start of the query time range (read from metadata, returns null if not present) */
    public Instant timeRangeStart() {
        return metadata != null ? castInstant(metadata.get(META_TIME_RANGE_START)) : null;
    }

    /** Get the end of the query time range (read from metadata, returns null if not present) */
    public Instant timeRangeEnd() {
        return metadata != null ? castInstant(metadata.get(META_TIME_RANGE_END)) : null;
    }

    /** Whether a time range is specified */
    public boolean hasTimeRange() {
        return timeRangeStart() != null || timeRangeEnd() != null;
    }

    private static Instant castInstant(Object value) {
        return value instanceof Instant instant ? instant : null;
    }
}
