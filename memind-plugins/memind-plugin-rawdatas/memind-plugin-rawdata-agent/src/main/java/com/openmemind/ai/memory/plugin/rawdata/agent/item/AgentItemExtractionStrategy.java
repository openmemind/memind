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
package com.openmemind.ai.memory.plugin.rawdata.agent.item;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHintConverter;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentExtractionOptions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Agent-specific item extraction strategy.
 *
 * <p>Task 9 adds deterministic TOOL/RESOLUTION extraction and Task 10 adds LLM
 * PLAYBOOK/DIRECTIVE extraction.
 */
public class AgentItemExtractionStrategy implements ItemExtractionStrategy {

    private final StructuredChatClient chatClient;
    private final PromptRegistry promptRegistry;
    private final AgentExtractionOptions options;
    private final AgentMemoryItemFactory memoryItemFactory;

    public AgentItemExtractionStrategy() {
        this(null, PromptRegistry.EMPTY, AgentExtractionOptions.defaults());
    }

    public AgentItemExtractionStrategy(
            StructuredChatClient chatClient,
            PromptRegistry promptRegistry,
            AgentExtractionOptions options) {
        this(chatClient, promptRegistry, options, new AgentMemoryItemFactory());
    }

    public AgentItemExtractionStrategy(
            StructuredChatClient chatClient,
            PromptRegistry promptRegistry,
            AgentExtractionOptions options,
            AgentMemoryItemFactory memoryItemFactory) {
        this.chatClient = chatClient;
        this.promptRegistry = promptRegistry == null ? PromptRegistry.EMPTY : promptRegistry;
        this.options = options == null ? AgentExtractionOptions.defaults() : options;
        this.memoryItemFactory =
                memoryItemFactory == null ? new AgentMemoryItemFactory() : memoryItemFactory;
    }

    @Override
    public Mono<List<ExtractedMemoryEntry>> extract(
            List<ParsedSegment> segments,
            List<MemoryInsightType> insightTypes,
            ItemExtractionConfig config) {
        if (segments == null || segments.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(segments)
                .flatMap(segment -> extractSegment(segment, config))
                .flatMapIterable(entries -> entries)
                .collectList();
    }

    private Mono<List<ExtractedMemoryEntry>> extractSegment(
            ParsedSegment segment, ItemExtractionConfig config) {
        List<ExtractedMemoryEntry> deterministicEntries =
                memoryItemFactory.deterministicEntries(segment);
        List<String> categories = enabledCategories(segment, config);
        if (chatClient == null || categories.isEmpty()) {
            return Mono.just(deterministicEntries);
        }

        var prompt = AgentItemPrompts.build(segment, categories).render(language(config));
        return chatClient
                .call(
                        ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt()),
                        MemoryItemExtractionResponse.class)
                .map(
                        response ->
                                merge(
                                        deterministicEntries,
                                        toLlmEntries(response, segment, categories)))
                .switchIfEmpty(Mono.just(deterministicEntries))
                .onErrorResume(ignored -> Mono.just(deterministicEntries));
    }

    private List<String> enabledCategories(ParsedSegment segment, ItemExtractionConfig config) {
        if (!isAgentEpisode(segment) || segment.metadata() == null) {
            return List.of();
        }
        int eventCount = stringList(segment.metadata().get("eventIds")).size();
        if (eventCount < options.minEventsForExtraction()) {
            return List.of();
        }

        Set<MemoryCategory> allowed =
                config == null || config.allowedCategories() == null
                        ? EnumSet.allOf(MemoryCategory.class)
                        : config.allowedCategories();
        var categories = new ArrayList<String>();
        addIfEnabled(categories, allowed, MemoryCategory.PROFILE, true);
        addIfEnabled(categories, allowed, MemoryCategory.BEHAVIOR, true);
        addIfEnabled(categories, allowed, MemoryCategory.EVENT, true);
        addIfEnabled(categories, allowed, MemoryCategory.TOOL, options.extractTool());
        addIfEnabled(categories, allowed, MemoryCategory.RESOLUTION, options.extractResolution());
        addIfEnabled(
                categories,
                allowed,
                MemoryCategory.PLAYBOOK,
                options.extractPlaybook()
                        && (eventCount >= options.minEventsForPlaybook()
                                || hasHighSignalSubagentEvidence(segment.metadata()))
                        && (!options.requireSuccessForPlaybook()
                                || successfulOutcome(segment.metadata().get("outcome"))));
        addIfEnabled(categories, allowed, MemoryCategory.DIRECTIVE, options.extractDirective());
        return List.copyOf(categories);
    }

    private static void addIfEnabled(
            List<String> categories,
            Set<MemoryCategory> allowed,
            MemoryCategory category,
            boolean enabled) {
        if (enabled && allowed.contains(category)) {
            categories.add(category.categoryName());
        }
    }

    private static boolean successfulOutcome(Object outcome) {
        String value = string(outcome);
        return "success".equalsIgnoreCase(value) || "partial_success".equalsIgnoreCase(value);
    }

    private static boolean hasHighSignalSubagentEvidence(Map<String, Object> metadata) {
        if (metadata == null) {
            return false;
        }
        return stringList(metadata.get("eventKinds")).stream()
                        .anyMatch(kind -> "subagent_stop".equalsIgnoreCase(kind))
                || stringList(metadata.get("toolNames")).stream()
                        .anyMatch(tool -> "task".equalsIgnoreCase(tool))
                || stringList(metadata.get("eventIds")).stream()
                        .anyMatch(eventId -> eventId.toLowerCase(Locale.ROOT).contains("subagent"));
    }

    private static List<ExtractedMemoryEntry> merge(
            List<ExtractedMemoryEntry> deterministicEntries,
            List<ExtractedMemoryEntry> llmEntries) {
        if (llmEntries.isEmpty()) {
            return deterministicEntries;
        }
        var merged =
                new ArrayList<ExtractedMemoryEntry>(
                        deterministicEntries.size() + llmEntries.size());
        merged.addAll(deterministicEntries);
        merged.addAll(llmEntries);
        return List.copyOf(merged);
    }

    private List<ExtractedMemoryEntry> toLlmEntries(
            MemoryItemExtractionResponse response, ParsedSegment segment, List<String> categories) {
        if (response == null || response.items() == null || response.items().isEmpty()) {
            return List.of();
        }
        return response.items().stream()
                .filter(item -> isValidItem(item, segment, categories))
                .map(item -> toEntry(item, segment))
                .toList();
    }

    private static ExtractedMemoryEntry toEntry(
            MemoryItemExtractionResponse.ExtractedItem item, ParsedSegment segment) {
        return new ExtractedMemoryEntry(
                item.content(),
                clamp(item.confidence()),
                occurredAt(item.occurredAt()),
                null,
                null,
                null,
                observedAt(segment),
                segment.rawDataId(),
                null,
                insightTypes(item),
                metadata(segment, item),
                MemoryItemType.FACT,
                normalize(item.category()),
                ExtractedGraphHintConverter.from(item));
    }

    private static boolean isValidItem(
            MemoryItemExtractionResponse.ExtractedItem item,
            ParsedSegment segment,
            List<String> categories) {
        if (item == null || item.content() == null || item.content().isBlank()) {
            return false;
        }
        String category = normalize(item.category());
        if (!categories.contains(category)) {
            return false;
        }
        List<String> expectedInsightTypes = expectedInsightTypes(category);
        if (expectedInsightTypes.isEmpty()
                || item.insightTypes() == null
                || item.insightTypes().stream().noneMatch(expectedInsightTypes::contains)) {
            return false;
        }
        List<String> evidenceEventIds = evidenceEventIds(item.metadata());
        if (evidenceEventIds.isEmpty()) {
            return false;
        }
        List<String> segmentEventIds = stringList(segment.metadata().get("eventIds"));
        if (!segmentEventIds.containsAll(evidenceEventIds)) {
            return false;
        }
        if (MemoryCategory.PLAYBOOK.categoryName().equals(category) && !validPlaybook(item)) {
            return false;
        }
        return !MemoryCategory.RESOLUTION.categoryName().equals(category) || validResolution(item);
    }

    private static boolean validPlaybook(MemoryItemExtractionResponse.ExtractedItem item) {
        Map<String, Object> metadata = item.metadata();
        return metadata != null
                && !string(metadata.get("trigger")).isBlank()
                && stringList(metadata.get("steps")).size() >= 2
                && !string(metadata.get("expectedOutcome")).isBlank();
    }

    private static boolean validResolution(MemoryItemExtractionResponse.ExtractedItem item) {
        Map<String, Object> metadata = item.metadata();
        return metadata != null
                && !string(metadata.get("problem")).isBlank()
                && (!string(metadata.get("fix")).isBlank()
                        || !string(metadata.get("conclusion")).isBlank());
    }

    private static Map<String, Object> metadata(
            ParsedSegment segment, MemoryItemExtractionResponse.ExtractedItem item) {
        var metadata = new LinkedHashMap<String, Object>();
        if (item.metadata() != null) {
            metadata.putAll(item.metadata());
        }
        copy(segment.metadata(), metadata, "episodeId");
        copy(segment.metadata(), metadata, "sessionId");
        copy(segment.metadata(), metadata, "timelineId");
        copy(segment.metadata(), metadata, "sourceClient");
        copy(segment.metadata(), metadata, "projectId");
        copy(segment.metadata(), metadata, "projectSlug");
        copy(segment.metadata(), metadata, "projectName");
        copy(segment.metadata(), metadata, "projectRootHash");
        copy(segment.metadata(), metadata, "gitBranch");
        copy(segment.metadata(), metadata, "outcome");
        copy(segment.metadata(), metadata, "files");
        copy(segment.metadata(), metadata, "commands");
        copy(segment.metadata(), metadata, "toolNames");
        copy(segment.metadata(), metadata, "failureSignals");
        return Map.copyOf(metadata);
    }

    private static void copy(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source != null && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private static List<String> evidenceEventIds(Map<String, Object> metadata) {
        return metadata == null ? List.of() : stringList(metadata.get("evidenceEventIds"));
    }

    private static List<String> insightTypes(MemoryItemExtractionResponse.ExtractedItem item) {
        List<String> expected = expectedInsightTypes(item.category());
        return item.insightTypes().stream().filter(expected::contains).distinct().toList();
    }

    private static List<String> expectedInsightTypes(String category) {
        return switch (normalize(category)) {
            case "profile" -> List.of("identity", "preferences", "relationships");
            case "behavior" -> List.of("behavior");
            case "event" -> List.of("experiences");
            case "tool" -> List.of("tools");
            case "resolution" -> List.of("resolutions");
            case "playbook" -> List.of("playbooks");
            case "directive" -> List.of("directives");
            default -> List.of();
        };
    }

    private static Instant occurredAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Instant observedAt(ParsedSegment segment) {
        return segment.runtimeContext() == null ? null : segment.runtimeContext().observedAt();
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static boolean isAgentEpisode(ParsedSegment segment) {
        return segment != null
                && segment.metadata() != null
                && "agent_episode".equals(segment.metadata().get("segmentType"));
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(AgentItemExtractionStrategy::string)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String language(ItemExtractionConfig config) {
        return config == null ? "en" : config.language();
    }

    public StructuredChatClient chatClient() {
        return chatClient;
    }

    public PromptRegistry promptRegistry() {
        return promptRegistry;
    }

    public AgentExtractionOptions options() {
        return options;
    }

    public AgentMemoryItemFactory memoryItemFactory() {
        return memoryItemFactory;
    }
}
