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

        /* Vector ID (null when not indexed) */
        String vectorId,

        /* Source data reference ID (matches MemoryRawData.id, UUID string) */
        String rawDataId,

        /* Content hash (for deduplication) */
        String contentHash,

        /* Semantic memory occurrence time (null for non-temporal items) */
        Instant occurredAt,

        /* Source observation time from the original message/segment (null when unavailable) */
        Instant observedAt,

        /* Additional key-value metadata (including insightTypes, whenToUse, etc.) */
        Map<String, Object> metadata,

        /* Creation time */
        Instant createdAt,

        /* Item type (FACT / FORESIGHT) */
        MemoryItemType type) {}
