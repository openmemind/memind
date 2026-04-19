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
     * Structured temporal extraction result.
     *
     * @param expression Original temporal phrase from source text
     * @param start Normalized lower bound in ISO-8601 UTC
     * @param end Normalized exclusive upper bound in ISO-8601 UTC
     * @param granularity Temporal granularity
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedTime(String expression, String start, String end, String granularity) {}

    /**
     * Structured entity hint extracted from the same response as the item.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedAliasObservation(
            String aliasSurface, String aliasClass, String evidenceSource, Float confidence) {}

    /**
     * Structured entity hint extracted from the same response as the item.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedEntity(
            String name,
            String entityType,
            Float salience,
            List<ExtractedAliasObservation> aliasObservations) {

        public ExtractedEntity {
            aliasObservations =
                    aliasObservations == null ? List.of() : List.copyOf(aliasObservations);
        }

        public ExtractedEntity(String name, String entityType, Float salience) {
            this(name, entityType, salience, List.of());
        }
    }

    /**
     * Structured causal hint referencing an earlier item in the same response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedCausalRelation(
            Integer targetIndex, String relationType, Float strength) {}

    /**
     * Single extraction result
     *
     * @param content Memory content
     * @param confidence Confidence level (0.0-1.0)
     * @param occurredAt Memory occurrence time ISO-8601 (LLM parsed, may be null)
     * @param time Structured temporal extraction result
     * @param insightTypes List of matched InsightType names (may be empty)
     * @param metadata Additional metadata (may be null; tool items may include tool-scoped keys
     *     such as whenToUse)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedItem(
            String content,
            float confidence,
            String occurredAt,
            ExtractedTime time,
            List<String> insightTypes,
            Map<String, Object> metadata,
            String category,
            List<ExtractedEntity> entities,
            List<ExtractedCausalRelation> causalRelations) {

        public ExtractedItem {
            insightTypes = insightTypes == null ? List.of() : List.copyOf(insightTypes);
            entities = entities == null ? List.of() : List.copyOf(entities);
            causalRelations = causalRelations == null ? List.of() : List.copyOf(causalRelations);
        }

        public ExtractedItem(
                String content,
                float confidence,
                String occurredAt,
                ExtractedTime time,
                List<String> insightTypes,
                Map<String, Object> metadata,
                String category) {
            this(
                    content,
                    confidence,
                    occurredAt,
                    time,
                    insightTypes,
                    metadata,
                    category,
                    List.of(),
                    List.of());
        }

        public ExtractedItem(
                String content,
                float confidence,
                String occurredAt,
                List<String> insightTypes,
                Map<String, Object> metadata,
                String category) {
            this(
                    content,
                    confidence,
                    occurredAt,
                    null,
                    insightTypes,
                    metadata,
                    category,
                    List.of(),
                    List.of());
        }
    }
}
