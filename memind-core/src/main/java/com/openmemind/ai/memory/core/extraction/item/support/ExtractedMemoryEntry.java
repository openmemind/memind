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
package com.openmemind.ai.memory.core.extraction.item.support;

import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Intermediate result of the extracted memory entry
 *
 * <p>Intermediate state after parsing from LLM response, before deduplication/persistence
 *
 * @param content Memory content
 * @param confidence Confidence level (used only for filtering, not persisted)
 * @param occurredAt Semantic time when the memory occurred (null for non-temporal items)
 * @param observedAt Source observation time from the original message/segment
 * @param rawDataId Source data ID
 * @param contentHash Content hash (filled after deduplication)
 * @param insightTypes List of matched InsightType names (can be empty)
 * @param metadata Additional metadata (can be null; merged from ParsedSegment.metadata + LLM output metadata, including whenToUse)
 */
public record ExtractedMemoryEntry(
        String content,
        float confidence,
        Instant occurredAt,
        Instant observedAt,
        String rawDataId,
        String contentHash,
        List<String> insightTypes,
        Map<String, Object> metadata,
        MemoryItemType type,
        String category) {

    /**
     * Set contentHash
     */
    public ExtractedMemoryEntry withContentHash(String contentHash) {
        return new ExtractedMemoryEntry(
                content,
                confidence,
                occurredAt,
                observedAt,
                rawDataId,
                contentHash,
                insightTypes,
                metadata,
                type,
                category);
    }
}
