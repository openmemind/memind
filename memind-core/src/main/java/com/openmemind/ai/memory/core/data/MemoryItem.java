package com.openmemind.ai.memory.core.data;

import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import java.time.Instant;
import java.util.Map;

/**
 * Memory Item
 */
public record MemoryItem(
        Long id,
        String memoryId,

        /* Memory content text */
        String content,

        /* Belonging scope */
        MemoryScope scope,

        /* Memory category */
        MemoryCategory category,

        /* Content type identifier (e.g. ContentTypes.CONVERSATION) */
        String contentType,

        /* Vector ID (null when not indexed) */
        String vectorId,

        /* Source data reference ID (matches MemoryRawData.id, UUID string) */
        String rawDataId,

        /* Content hash (for deduplication) */
        String contentHash,

        /* Memory occurrence time (LLM parsing or source message timestamp fallback, null=permanent fact) */
        Instant occurredAt,

        /* Additional key-value metadata (including insightTypes, whenToUse, etc.) */
        Map<String, Object> metadata,

        /* Creation time */
        Instant createdAt,

        /* Item type (FACT / FORESIGHT) */
        MemoryItemType type) {}
