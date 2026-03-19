package com.openmemind.ai.memory.core.extraction.item.strategy;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.item.support.ForesightExtractionResponse;
import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.prompt.PromptResult;
import com.openmemind.ai.memory.core.prompt.extraction.item.ForesightPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.item.MemoryItemPrompts;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Default item extraction strategy for conversation content.
 *
 * <p>Extracts atomic facts (FACT) and foresight predictions (FORESIGHT) in parallel
 * from parsed conversation segments using Spring AI structured output.
 */
public class DefaultItemExtractionStrategy implements ItemExtractionStrategy {

    private final ChatClient chatClient;
    private final Set<MemoryCategory> categories;

    public DefaultItemExtractionStrategy(ChatClient chatClient, Set<MemoryCategory> categories) {
        this.chatClient = chatClient;
        this.categories = categories;
    }

    @Override
    public Mono<List<ExtractedMemoryEntry>> extract(
            List<ParsedSegment> segments,
            List<MemoryInsightType> insightTypes,
            ItemExtractionConfig config) {

        var enableForesight = config.enableForesight();
        var language = config.language();

        Mono<List<ExtractedMemoryEntry>> factMono =
                Flux.fromIterable(segments)
                        .flatMap(segment -> extractFact(insightTypes, segment, language))
                        .flatMapIterable(list -> list)
                        .collectList();

        // Foresight only applies when explicitly enabled
        Mono<List<ExtractedMemoryEntry>> foresightMono =
                enableForesight
                        ? Flux.fromIterable(segments)
                                .flatMap(segment -> extractForesight(segment, language))
                                .flatMapIterable(list -> list)
                                .collectList()
                                .onErrorResume(e -> Mono.just(List.of()))
                        : Mono.just(List.of());

        return Mono.zip(factMono, foresightMono)
                .map(
                        tuple -> {
                            var merged = new ArrayList<>(tuple.getT1());
                            merged.addAll(tuple.getT2());
                            return List.copyOf(merged);
                        });
    }

    private Mono<List<ExtractedMemoryEntry>> extractFact(
            List<MemoryInsightType> insightTypes, ParsedSegment segment, String language) {

        var referenceTime = resolveReferenceTime(segment);

        return Mono.fromCallable(
                        () -> {
                            var userName = resolveUserName(segment);
                            return MemoryItemPrompts.buildUnified(
                                            insightTypes,
                                            segment.text(),
                                            referenceTime,
                                            userName,
                                            categories)
                                    .render(language);
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(prompt -> callLlmForItems(prompt, segment, referenceTime));
    }

    private Mono<List<ExtractedMemoryEntry>> callLlmForItems(
            PromptResult prompt, ParsedSegment segment, Instant referenceTime) {
        return Mono.fromCallable(
                        () -> {
                            MemoryItemExtractionResponse response =
                                    chatClient
                                            .prompt()
                                            .system(prompt.systemPrompt())
                                            .user(prompt.userPrompt())
                                            .call()
                                            .entity(MemoryItemExtractionResponse.class);

                            if (response == null || response.items() == null) {
                                return List.<ExtractedMemoryEntry>of();
                            }

                            return response.items().stream()
                                    .map(
                                            item ->
                                                    new ExtractedMemoryEntry(
                                                            item.content(),
                                                            clamp(item.confidence()),
                                                            resolveOccurredAt(
                                                                    item.occurredAt(),
                                                                    referenceTime),
                                                            segment.rawDataId(),
                                                            null,
                                                            item.insightTypes() != null
                                                                    ? item.insightTypes()
                                                                    : List.of(),
                                                            mergeMetadata(segment, item),
                                                            MemoryItemType.FACT,
                                                            item.category()))
                                    .toList();
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(2, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(5)))
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private Mono<List<ExtractedMemoryEntry>> extractForesight(
            ParsedSegment segment, String language) {

        return Mono.fromCallable(
                        () -> {
                            var referenceTime = resolveReferenceTime(segment);
                            var prompt =
                                    ForesightPrompts.build(segment.text(), referenceTime)
                                            .render(language);

                            ForesightExtractionResponse response =
                                    chatClient
                                            .prompt()
                                            .system(prompt.systemPrompt())
                                            .user(prompt.userPrompt())
                                            .call()
                                            .entity(ForesightExtractionResponse.class);

                            if (response == null || response.items() == null) {
                                return List.<ExtractedMemoryEntry>of();
                            }

                            return response.items().stream()
                                    .filter(
                                            item ->
                                                    item.content() != null
                                                            && !item.content().isBlank())
                                    .map(item -> toForesightEntry(item, segment, referenceTime))
                                    .toList();
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private static ExtractedMemoryEntry toForesightEntry(
            ForesightExtractionResponse.ForesightItem item,
            ParsedSegment segment,
            Instant referenceTime) {

        var metadata = new LinkedHashMap<String, Object>();
        if (item.evidence() != null && !item.evidence().isBlank()) {
            metadata.put("evidence", item.evidence());
        }
        if (item.validUntil() != null && !item.validUntil().isBlank()) {
            metadata.put("validUntil", item.validUntil());
        }
        if (item.durationDays() != null) {
            metadata.put("durationDays", item.durationDays());
        }
        if (item.metadata() != null) {
            metadata.putAll(item.metadata());
        }

        return new ExtractedMemoryEntry(
                item.content(),
                1.0f,
                referenceTime,
                segment.rawDataId(),
                null,
                List.of(),
                metadata.isEmpty() ? null : Map.copyOf(metadata),
                MemoryItemType.FORESIGHT,
                null);
    }

    static Instant resolveOccurredAt(String llmValue, Instant fallback) {
        if (llmValue != null && !llmValue.isBlank()) {
            try {
                return Instant.parse(llmValue);
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    static Map<String, Object> mergeMetadata(
            ParsedSegment segment, MemoryItemExtractionResponse.ExtractedItem item) {
        var merged = new LinkedHashMap<String, Object>();
        if (segment.metadata() != null) {
            merged.putAll(segment.metadata());
            merged.remove("messages");
        }
        if (item.metadata() != null) {
            merged.putAll(item.metadata());
        }
        return merged.isEmpty() ? null : Map.copyOf(merged);
    }

    static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    static Instant resolveReferenceTime(ParsedSegment segment) {
        if (segment.metadata() != null) {
            Object messagesObj = segment.metadata().get("messages");
            if (messagesObj instanceof List<?> messageList && !messageList.isEmpty()) {
                Object last = messageList.getLast();
                if (last instanceof Message m && m.timestamp() != null) {
                    return m.timestamp();
                }
            }
        }
        return Instant.now();
    }

    static String resolveUserName(ParsedSegment segment) {
        if (segment.metadata() != null) {
            Object messagesObj = segment.metadata().get("messages");
            if (messagesObj instanceof List<?> messageList) {
                for (Object obj : messageList) {
                    if (obj instanceof Message m
                            && m.role() == Message.Role.USER
                            && m.userName() != null
                            && !m.userName().isBlank()) {
                        return m.userName();
                    }
                }
            }
        }
        return null;
    }
}
