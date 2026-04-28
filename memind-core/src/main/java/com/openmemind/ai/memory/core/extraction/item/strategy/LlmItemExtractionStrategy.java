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
package com.openmemind.ai.memory.core.extraction.item.strategy;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasClass;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedTemporal;
import com.openmemind.ai.memory.core.extraction.item.support.ForesightExtractionResponse;
import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import com.openmemind.ai.memory.core.extraction.item.support.TemporalNormalizer;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptResult;
import com.openmemind.ai.memory.core.prompt.extraction.item.ForesightPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.item.MemoryItemPrompts;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * LLM item extraction strategy for conversation content.
 *
 * <p>Extracts atomic facts (FACT) and foresight predictions (FORESIGHT) in parallel
 * from parsed conversation segments using the framework-neutral LLM SPI.
 */
public class LlmItemExtractionStrategy implements ItemExtractionStrategy {

    private static final Logger log = LoggerFactory.getLogger(LlmItemExtractionStrategy.class);

    private final StructuredChatClient structuredChatClient;
    private final PromptRegistry promptRegistry;

    public LlmItemExtractionStrategy(StructuredChatClient structuredChatClient) {
        this(structuredChatClient, PromptRegistry.EMPTY);
    }

    public LlmItemExtractionStrategy(
            StructuredChatClient structuredChatClient, PromptRegistry promptRegistry) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
        this.promptRegistry =
                Objects.requireNonNull(promptRegistry, "promptRegistry must not be null");
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
                        .flatMap(segment -> extractFact(insightTypes, segment, config, language))
                        .flatMapIterable(list -> list)
                        .collectList();

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
            List<MemoryInsightType> insightTypes,
            ParsedSegment segment,
            ItemExtractionConfig config,
            String language) {
        var referenceTime = resolveReferenceTime(segment);

        return Mono.fromCallable(
                        () -> {
                            var userName = resolveUserName(segment);
                            return MemoryItemPrompts.buildUnified(
                                            promptRegistry,
                                            insightTypes,
                                            segment.text(),
                                            referenceTime,
                                            userName,
                                            config.allowedCategories(),
                                            config.graph())
                                    .render(language);
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(prompt -> callLlmForItems(prompt, segment, referenceTime));
    }

    private Mono<List<ExtractedMemoryEntry>> callLlmForItems(
            PromptResult prompt, ParsedSegment segment, Instant referenceTime) {
        logGraphPromptSummary(prompt);
        var messages = ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt());
        return structuredChatClient
                .call(messages, MemoryItemExtractionResponse.class)
                .doOnNext(LlmItemExtractionStrategy::logRawGraphHints)
                .map(
                        response -> {
                            var entries = toFactEntries(response, segment, referenceTime);
                            logExtractedGraphHints(entries);
                            return entries;
                        })
                .switchIfEmpty(Mono.just(List.of()))
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(2, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(5)))
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private static void logGraphPromptSummary(PromptResult prompt) {
        String rendered =
                (prompt.systemPrompt() == null ? "" : prompt.systemPrompt())
                        + "\n"
                        + (prompt.userPrompt() == null ? "" : prompt.userPrompt());
        boolean graphHintsEnabled = rendered.contains("## Graph Hints");
        if (!graphHintsEnabled) {
            return;
        }
        log.debug(
                "Item graph prompt: enabled={} causeIndex={} effectIndex={} goodEntities={}"
                        + " badCausal={}",
                true,
                rendered.contains("causeIndex"),
                rendered.contains("effectIndex"),
                rendered.contains("Good entities"),
                rendered.contains("Bad causal relation"));
    }

    private static void logRawGraphHints(MemoryItemExtractionResponse response) {
        if (response == null || response.items() == null || response.items().isEmpty()) {
            log.info("Item graph raw response: no items");
            return;
        }
        for (int index = 0; index < response.items().size(); index++) {
            int itemIndex = index;
            var item = response.items().get(itemIndex);
            int entityCount = item.entities() == null ? 0 : item.entities().size();
            int causalCount = item.causalRelations() == null ? 0 : item.causalRelations().size();
            log.debug(
                    "Item graph raw response item[{}]: content='{}' entities={} causalRelations={}",
                    itemIndex,
                    abbreviate(item.content()),
                    entityCount,
                    causalCount);
            if (item.entities() != null) {
                item.entities()
                        .forEach(
                                entity ->
                                        log.debug(
                                                "  raw entity item[{}]: name='{}' type={}"
                                                        + " salience={} aliases={}",
                                                itemIndex,
                                                entity.name(),
                                                entity.entityType(),
                                                entity.salience(),
                                                entity.aliasObservations() == null
                                                        ? 0
                                                        : entity.aliasObservations().size()));
            }
            if (item.causalRelations() != null) {
                item.causalRelations()
                        .forEach(
                                relation ->
                                        log.debug(
                                                "  raw causal item[{}]: causeIndex={}"
                                                    + " effectIndex={} relationType={} strength={}",
                                                itemIndex,
                                                relation.causeIndex(),
                                                relation.effectIndex(),
                                                relation.relationType(),
                                                relation.strength()));
            }
        }
    }

    private static void logExtractedGraphHints(List<ExtractedMemoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            log.info("Item graph extracted entries: no items");
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            int entryIndex = index;
            var entry = entries.get(entryIndex);
            var graphHints = entry.graphHints();
            log.debug(
                    "Item graph extracted entry[{}]: content='{}' entities={} causalRelations={}",
                    entryIndex,
                    abbreviate(entry.content()),
                    graphHints.entities().size(),
                    graphHints.causalRelations().size());
            graphHints
                    .entities()
                    .forEach(
                            entity ->
                                    log.debug(
                                            "  extracted entity entry[{}]: name='{}' type={}"
                                                    + " salience={} aliases={}",
                                            entryIndex,
                                            entity.name(),
                                            entity.entityType(),
                                            entity.salience(),
                                            entity.aliasObservations().size()));
            graphHints
                    .causalRelations()
                    .forEach(
                            relation ->
                                    log.debug(
                                            "  extracted causal entry[{}]: causeIndex={}"
                                                    + " effectIndex={} relationType={} strength={}",
                                            entryIndex,
                                            relation.causeIndex(),
                                            relation.effectIndex(),
                                            relation.relationType(),
                                            relation.strength()));
        }
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 157) + "...";
    }

    static List<ExtractedMemoryEntry> toFactEntries(
            MemoryItemExtractionResponse response, ParsedSegment segment, Instant referenceTime) {
        if (response == null || response.items() == null) {
            return List.of();
        }

        var observedAt = resolveObservedAt(segment);

        return response.items().stream()
                .map(item -> toFactEntry(item, observedAt, segment, referenceTime))
                .toList();
    }

    private static ExtractedMemoryEntry toFactEntry(
            MemoryItemExtractionResponse.ExtractedItem item,
            Instant observedAt,
            ParsedSegment segment,
            Instant referenceTime) {
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
                segment.rawDataId(),
                null,
                item.insightTypes() != null ? item.insightTypes() : List.of(),
                mergeMetadata(segment, item, temporal),
                MemoryItemType.FACT,
                item.category(),
                new ExtractedGraphHints(
                        toEntityHints(item.entities()), toCausalHints(item.causalRelations())));
    }

    private Mono<List<ExtractedMemoryEntry>> extractForesight(
            ParsedSegment segment, String language) {
        return Mono.fromCallable(
                        () ->
                                ForesightPrompts.build(
                                                promptRegistry,
                                                segment.text(),
                                                resolveReferenceTime(segment))
                                        .render(language))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(
                        prompt ->
                                structuredChatClient
                                        .call(
                                                ChatMessages.systemUser(
                                                        prompt.systemPrompt(), prompt.userPrompt()),
                                                ForesightExtractionResponse.class)
                                        .map(
                                                response ->
                                                        toForesightEntries(
                                                                response,
                                                                segment,
                                                                resolveReferenceTime(segment)))
                                        .switchIfEmpty(Mono.just(List.of())))
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private List<ExtractedMemoryEntry> toForesightEntries(
            ForesightExtractionResponse response, ParsedSegment segment, Instant referenceTime) {
        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items().stream()
                .filter(item -> item.content() != null && !item.content().isBlank())
                .map(item -> toForesightEntry(item, segment, referenceTime))
                .toList();
    }

    private static ExtractedMemoryEntry toForesightEntry(
            ForesightExtractionResponse.ForesightItem item,
            ParsedSegment segment,
            Instant referenceTime) {

        var observedAt = resolveObservedAt(segment);
        var metadata = new LinkedHashMap<String, Object>();
        if (segment.metadata() != null) {
            metadata.putAll(segment.metadata());
            metadata.remove("messages");
        }
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
                observedAt,
                segment.rawDataId(),
                null,
                List.of(),
                metadata.isEmpty() ? null : Map.copyOf(metadata),
                MemoryItemType.FORESIGHT,
                null);
    }

    static Instant resolveOccurredAt(String llmValue) {
        if (llmValue != null && !llmValue.isBlank()) {
            try {
                return Instant.parse(llmValue);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static Instant resolveObservedAt(ParsedSegment segment) {
        return segment.runtimeContext() != null ? segment.runtimeContext().observedAt() : null;
    }

    static Map<String, Object> mergeMetadata(
            ParsedSegment segment, MemoryItemExtractionResponse.ExtractedItem item) {
        return mergeMetadata(segment, item, null);
    }

    static Map<String, Object> mergeMetadata(
            ParsedSegment segment,
            MemoryItemExtractionResponse.ExtractedItem item,
            ExtractedTemporal temporal) {
        var merged = new LinkedHashMap<String, Object>();
        if (segment.metadata() != null) {
            merged.putAll(segment.metadata());
            merged.remove("messages");
        }
        if (item.metadata() != null) {
            merged.putAll(item.metadata());
        }
        if (item.threadSemantics() != null && !item.threadSemantics().isEmpty()) {
            merged.put("threadSemantics", item.threadSemantics().toMetadataValue());
        }
        if (temporal != null && temporal.expression() != null && !temporal.expression().isBlank()) {
            merged.put("timeExpression", temporal.expression());
        }
        return merged.isEmpty() ? null : Map.copyOf(merged);
    }

    static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static Float clampNullable(Float value) {
        return value == null ? null : clamp(value);
    }

    private static List<ExtractedGraphHints.ExtractedEntityHint> toEntityHints(
            List<MemoryItemExtractionResponse.ExtractedEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .filter(
                        entity ->
                                entity != null && entity.name() != null && !entity.name().isBlank())
                .map(
                        entity ->
                                new ExtractedGraphHints.ExtractedEntityHint(
                                        entity.name(),
                                        entity.entityType(),
                                        clampNullable(entity.salience()),
                                        toAliasObservations(entity.aliasObservations())))
                .toList();
    }

    private static List<EntityAliasObservation> toAliasObservations(
            List<MemoryItemExtractionResponse.ExtractedAliasObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return List.of();
        }
        return observations.stream()
                .filter(Objects::nonNull)
                .map(
                        observation ->
                                EntityAliasClass.fromWireValue(observation.aliasClass())
                                        .map(
                                                aliasClass ->
                                                        new EntityAliasObservation(
                                                                observation.aliasSurface(),
                                                                aliasClass,
                                                                observation.evidenceSource(),
                                                                clampNullable(
                                                                        observation.confidence()))))
                .flatMap(Optional::stream)
                .toList();
    }

    private static List<ExtractedGraphHints.ExtractedCausalRelationHint> toCausalHints(
            List<MemoryItemExtractionResponse.ExtractedCausalRelation> causalRelations) {
        if (causalRelations == null || causalRelations.isEmpty()) {
            return List.of();
        }
        return causalRelations.stream()
                .filter(
                        relation ->
                                relation != null
                                        && relation.causeIndex() != null
                                        && relation.effectIndex() != null)
                .map(
                        relation ->
                                new ExtractedGraphHints.ExtractedCausalRelationHint(
                                        relation.causeIndex(),
                                        relation.effectIndex(),
                                        relation.relationType(),
                                        clampNullable(relation.strength())))
                .toList();
    }

    static Instant resolveReferenceTime(ParsedSegment segment) {
        var observedAt = resolveObservedAt(segment);
        return observedAt != null ? observedAt : Instant.now();
    }

    static String resolveUserName(ParsedSegment segment) {
        return segment.runtimeContext() != null ? segment.runtimeContext().userName() : null;
    }
}
