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
import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import com.openmemind.ai.memory.core.prompt.extraction.item.SelfVerificationPrompts;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Self-verification step -- optional extraction enhancement
 *
 */
public class SelfVerificationStep {

    private static final Logger log = LoggerFactory.getLogger(SelfVerificationStep.class);

    private final ChatClient chatClient;

    public SelfVerificationStep(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    public Mono<List<ExtractedMemoryEntry>> verify(
            String originalText, List<ExtractedMemoryEntry> existingEntries, String rawDataId) {
        return verify(
                originalText, existingEntries, rawDataId, Instant.now(), List.of(), null, null);
    }

    public Mono<List<ExtractedMemoryEntry>> verify(
            String originalText,
            List<ExtractedMemoryEntry> existingEntries,
            String rawDataId,
            Instant referenceTime) {
        return verify(
                originalText, existingEntries, rawDataId, referenceTime, List.of(), null, null);
    }

    public Mono<List<ExtractedMemoryEntry>> verify(
            String originalText,
            List<ExtractedMemoryEntry> existingEntries,
            String rawDataId,
            Instant referenceTime,
            List<MemoryInsightType> insightTypes) {
        return verify(
                originalText, existingEntries, rawDataId, referenceTime, insightTypes, null, null);
    }

    public Mono<List<ExtractedMemoryEntry>> verify(
            String originalText,
            List<ExtractedMemoryEntry> existingEntries,
            String rawDataId,
            Instant referenceTime,
            List<MemoryInsightType> insightTypes,
            String userName,
            Set<MemoryCategory> categories) {

        var promptResult =
                SelfVerificationPrompts.build(
                                originalText,
                                existingEntries,
                                referenceTime,
                                insightTypes,
                                userName,
                                categories)
                        .render(null);

        return Mono.fromCallable(
                        () -> {
                            MemoryItemExtractionResponse response =
                                    chatClient
                                            .prompt()
                                            .system(promptResult.systemPrompt())
                                            .user(promptResult.userPrompt())
                                            .call()
                                            .entity(MemoryItemExtractionResponse.class);

                            if (response == null || response.items() == null) {
                                return List.<ExtractedMemoryEntry>of();
                            }

                            return response.items().stream()
                                    .map(item -> toEntry(item, rawDataId, referenceTime))
                                    .toList();
                        })
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

    private static ExtractedMemoryEntry toEntry(
            MemoryItemExtractionResponse.ExtractedItem item,
            String rawDataId,
            Instant referenceTime) {
        return new ExtractedMemoryEntry(
                item.content(),
                clamp(item.confidence()),
                resolveOccurredAt(item.occurredAt(), referenceTime),
                rawDataId,
                null,
                item.insightTypes() != null ? item.insightTypes() : List.of(),
                item.metadata(),
                MemoryItemType.FACT,
                item.category());
    }

    private static Instant resolveOccurredAt(String llmValue, Instant fallback) {
        if (llmValue != null && !llmValue.isBlank()) {
            try {
                return Instant.parse(llmValue);
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
