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
package com.openmemind.ai.memory.core.extraction.item;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedTemporal;
import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import com.openmemind.ai.memory.core.extraction.item.support.TemporalNormalizer;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.extraction.item.SelfVerificationPrompts;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Self-verification step.
 */
public class LlmSelfVerificationStep {

    private static final Logger log = LoggerFactory.getLogger(LlmSelfVerificationStep.class);

    private final StructuredChatClient structuredChatClient;
    private final PromptRegistry promptRegistry;

    public LlmSelfVerificationStep(StructuredChatClient structuredChatClient) {
        this(structuredChatClient, PromptRegistry.EMPTY);
    }

    public LlmSelfVerificationStep(
            StructuredChatClient structuredChatClient, PromptRegistry promptRegistry) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
        this.promptRegistry =
                Objects.requireNonNull(promptRegistry, "promptRegistry must not be null");
    }

    public Mono<List<ExtractedMemoryEntry>> verify(
            String originalText, List<ExtractedMemoryEntry> existingEntries, String rawDataId) {
        return verify(
                originalText,
                existingEntries,
                rawDataId,
                Instant.now(),
                List.of(),
                null,
                null,
                null,
                null);
    }

    public Mono<List<ExtractedMemoryEntry>> verify(
            String originalText,
            List<ExtractedMemoryEntry> existingEntries,
            String rawDataId,
            Instant referenceTime) {
        return verify(
                originalText,
                existingEntries,
                rawDataId,
                referenceTime,
                List.of(),
                null,
                null,
                null,
                null);
    }

    public Mono<List<ExtractedMemoryEntry>> verify(
            String originalText,
            List<ExtractedMemoryEntry> existingEntries,
            String rawDataId,
            Instant referenceTime,
            List<MemoryInsightType> insightTypes) {
        return verify(
                originalText,
                existingEntries,
                rawDataId,
                referenceTime,
                insightTypes,
                null,
                null,
                null,
                null);
    }

    public Mono<List<ExtractedMemoryEntry>> verify(
            String originalText,
            List<ExtractedMemoryEntry> existingEntries,
            String rawDataId,
            Instant referenceTime,
            List<MemoryInsightType> insightTypes,
            String userName,
            Set<MemoryCategory> categories) {
        return verify(
                originalText,
                existingEntries,
                rawDataId,
                referenceTime,
                insightTypes,
                userName,
                categories,
                null,
                null);
    }

    public Mono<List<ExtractedMemoryEntry>> verify(
            String originalText,
            List<ExtractedMemoryEntry> existingEntries,
            String rawDataId,
            Instant referenceTime,
            List<MemoryInsightType> insightTypes,
            String userName,
            Set<MemoryCategory> categories,
            String language) {
        return verify(
                originalText,
                existingEntries,
                rawDataId,
                referenceTime,
                insightTypes,
                userName,
                categories,
                language,
                null);
    }

    public Mono<List<ExtractedMemoryEntry>> verify(
            String originalText,
            List<ExtractedMemoryEntry> existingEntries,
            String rawDataId,
            Instant referenceTime,
            List<MemoryInsightType> insightTypes,
            String userName,
            Set<MemoryCategory> categories,
            String language,
            Instant observedAt) {

        var promptResult =
                SelfVerificationPrompts.build(
                                promptRegistry,
                                originalText,
                                existingEntries,
                                referenceTime,
                                insightTypes,
                                userName,
                                categories)
                        .render(language);
        var messages =
                ChatMessages.systemUser(promptResult.systemPrompt(), promptResult.userPrompt());

        return structuredChatClient
                .call(messages, MemoryItemExtractionResponse.class)
                .map(response -> toEntries(response, rawDataId, referenceTime, observedAt))
                .switchIfEmpty(Mono.just(List.of()))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(
                        missed ->
                                log.debug(
                                        "Self-verification found {} missing memories"
                                                + " [rawDataId={}]",
                                        missed.size(),
                                        rawDataId))
                .onErrorResume(
                        ex -> {
                            log.warn(
                                    "Self-verification step failed, returning empty list"
                                            + " [rawDataId={}]: {}",
                                    rawDataId,
                                    ex.getMessage());
                            return Mono.just(List.of());
                        });
    }

    private List<ExtractedMemoryEntry> toEntries(
            MemoryItemExtractionResponse response,
            String rawDataId,
            Instant referenceTime,
            Instant observedAt) {
        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items().stream()
                .map(item -> toEntry(item, rawDataId, referenceTime, observedAt))
                .toList();
    }

    private static ExtractedMemoryEntry toEntry(
            MemoryItemExtractionResponse.ExtractedItem item,
            String rawDataId,
            Instant referenceTime,
            Instant observedAt) {
        ExtractedTemporal temporal =
                TemporalNormalizer.normalize(item.time(), item.occurredAt(), referenceTime);
        return new ExtractedMemoryEntry(
                item.content(),
                clamp(item.confidence()),
                temporal.compatibilityOccurredAt(),
                temporal.occurredStart(),
                temporal.occurredEnd(),
                temporal.granularity(),
                observedAt,
                rawDataId,
                null,
                item.insightTypes() != null ? item.insightTypes() : List.of(),
                mergeMetadata(item, temporal),
                MemoryItemType.FACT,
                item.category());
    }

    private static Map<String, Object> mergeMetadata(
            MemoryItemExtractionResponse.ExtractedItem item, ExtractedTemporal temporal) {
        var merged = new LinkedHashMap<String, Object>();
        if (item.metadata() != null) {
            merged.putAll(item.metadata());
        }
        if (temporal.expression() != null && !temporal.expression().isBlank()) {
            merged.put("timeExpression", temporal.expression());
        }
        return merged.isEmpty() ? null : Map.copyOf(merged);
    }

    private static Instant resolveOccurredAt(String llmValue) {
        if (llmValue != null && !llmValue.isBlank()) {
            try {
                return Instant.parse(llmValue);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
