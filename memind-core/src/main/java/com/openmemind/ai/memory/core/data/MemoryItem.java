/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

        /* Content type identifier (e.g. ConversationContent.TYPE) */
        String contentType,

        /* Source client that produced this memory item */
        String sourceClient,

        /* Vector ID (null when not indexed) */
        String vectorId,

        /* Source data reference ID (matches MemoryRawData.id, UUID string) */
        String rawDataId,

        /* Content hash (for deduplication) */
        String contentHash,

        /* Semantic memory occurrence time (null for non-temporal items) */
        Instant occurredAt,

        /* Normalized semantic interval lower bound (null for non-temporal items) */
        Instant occurredStart,

        /* Normalized semantic interval exclusive upper bound (null for open/point items) */
        Instant occurredEnd,

        /* Normalized temporal granularity */
        String timeGranularity,

        /* Source observation time from the original message/segment (null when unavailable) */
        Instant observedAt,

        /* Additional key-value metadata (including insightTypes and tool-scoped keys such as
         * whenToUse) */
        Map<String, Object> metadata,

        /* Creation time */
        Instant createdAt,

        /* Item type (FACT / FORESIGHT) */
        MemoryItemType type) {

    public MemoryItem {
        sourceClient = normalizeString(sourceClient);
    }

    public MemoryItem(
            Long id,
            String memoryId,
            String content,
            MemoryScope scope,
            MemoryCategory category,
            String contentType,
            String vectorId,
            String rawDataId,
            String contentHash,
            Instant occurredAt,
            Instant occurredStart,
            Instant occurredEnd,
            String timeGranularity,
            Instant observedAt,
            Map<String, Object> metadata,
            Instant createdAt,
            MemoryItemType type) {
        this(
                id,
                memoryId,
                content,
                scope,
                category,
                contentType,
                null,
                vectorId,
                rawDataId,
                contentHash,
                occurredAt,
                occurredStart,
                occurredEnd,
                timeGranularity,
                observedAt,
                metadata,
                createdAt,
                type);
    }

    public MemoryItem(
            Long id,
            String memoryId,
            String content,
            MemoryScope scope,
            MemoryCategory category,
            String contentType,
            String sourceClient,
            String vectorId,
            String rawDataId,
            String contentHash,
            Instant occurredAt,
            Instant observedAt,
            Map<String, Object> metadata,
            Instant createdAt,
            MemoryItemType type) {
        this(
                id,
                memoryId,
                content,
                scope,
                category,
                contentType,
                sourceClient,
                vectorId,
                rawDataId,
                contentHash,
                occurredAt,
                null,
                null,
                null,
                observedAt,
                metadata,
                createdAt,
                type);
    }

    public MemoryItem(
            Long id,
            String memoryId,
            String content,
            MemoryScope scope,
            MemoryCategory category,
            String contentType,
            String vectorId,
            String rawDataId,
            String contentHash,
            Instant occurredAt,
            Instant observedAt,
            Map<String, Object> metadata,
            Instant createdAt,
            MemoryItemType type) {
        this(
                id,
                memoryId,
                content,
                scope,
                category,
                contentType,
                null,
                vectorId,
                rawDataId,
                contentHash,
                occurredAt,
                null,
                null,
                null,
                observedAt,
                metadata,
                createdAt,
                type);
    }

    private static String normalizeString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
