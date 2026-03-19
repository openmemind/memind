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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * LLM Memory Extraction Structured Response
 *
 * @param items List of extracted memory items
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MemoryItemExtractionResponse(List<ExtractedItem> items) {

    /**
     * Single extraction result
     *
     * @param content Memory content
     * @param confidence Confidence level (0.0-1.0)
     * @param occurredAt Memory occurrence time ISO-8601 (LLM parsed, may be null)
     * @param insightTypes List of matched InsightType names (may be empty)
     * @param metadata Additional metadata (may be null; includes whenToUse, etc.)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedItem(
            String content,
            float confidence,
            String occurredAt,
            List<String> insightTypes,
            Map<String, Object> metadata,
            String category) {}
}
