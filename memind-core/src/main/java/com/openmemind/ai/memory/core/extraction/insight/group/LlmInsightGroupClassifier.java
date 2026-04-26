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
package com.openmemind.ai.memory.core.extraction.insight.group;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.extraction.insight.InsightGroupPrompts;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Insight semantic grouping classifier based on StructuredChatClient
 *
 */
public class LlmInsightGroupClassifier implements InsightGroupClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmInsightGroupClassifier.class);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LEADING_DATE =
            Pattern.compile("^(?:\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}(?:日)?)\\s*[:：-]?\\s*");
    private static final Pattern LEADING_METADATA =
            Pattern.compile("^(?:#{1,6}\\s*|[-*]\\s*|第[一二三四五六七八九十\\d]+[次阶段章节]\\s*)");
    private static final Pattern SPLIT_CLAUSE = Pattern.compile("[\\r\\n,，。；;！？!?：:]");
    private static final Pattern LEADING_SPEAKER =
            Pattern.compile(
                    "^(?:user|assistant|system|用户|助手|系统)\\s*[:：]\\s*", Pattern.CASE_INSENSITIVE);
    private static final Set<String> GENERIC_GROUP_NAMES =
            Set.of(
                    "general",
                    "other",
                    "misc",
                    "miscellaneous",
                    "unknown",
                    "note",
                    "notes",
                    "session note",
                    "session notes",
                    "session record",
                    "chat record",
                    "record",
                    "records",
                    "其他",
                    "其它",
                    "综合",
                    "杂项",
                    "通用",
                    "未知",
                    "记录",
                    "会话记录",
                    "对话记录",
                    "聊天记录");

    private final StructuredChatClient structuredChatClient;
    private final PromptRegistry promptRegistry;

    public LlmInsightGroupClassifier(StructuredChatClient structuredChatClient) {
        this(structuredChatClient, PromptRegistry.EMPTY);
    }

    public LlmInsightGroupClassifier(
            StructuredChatClient structuredChatClient, PromptRegistry promptRegistry) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
        this.promptRegistry =
                Objects.requireNonNull(promptRegistry, "promptRegistry must not be null");
    }

    @Override
    public Mono<Map<String, List<MemoryItem>>> classify(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames) {
        return classify(insightType, items, existingGroupNames, null, null);
    }

    @Override
    public Mono<Map<String, List<MemoryItem>>> classify(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames,
            String language) {
        return classify(insightType, items, existingGroupNames, null, language);
    }

    @Override
    public Mono<Map<String, List<MemoryItem>>> classify(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames,
            String additionalContext,
            String language) {

        if (items.isEmpty()) {
            return Mono.just(Map.of());
        }

        var promptResult =
                InsightGroupPrompts.build(
                                promptRegistry, insightType, items, existingGroupNames, language)
                        .render(language);
        var userPrompt =
                additionalContext != null && !additionalContext.isBlank()
                        ? promptResult.userPrompt()
                                + "\n\n<AdditionalContext>\n"
                                + additionalContext
                                + "\n</AdditionalContext>"
                        : promptResult.userPrompt();
        var messages = ChatMessages.systemUser(promptResult.systemPrompt(), userPrompt);

        // item id → item mapping
        Map<String, MemoryItem> itemById =
                items.stream()
                        .collect(
                                Collectors.toMap(
                                        item -> String.valueOf(item.id()),
                                        Function.identity(),
                                        (a, b) -> a));

        return structuredChatClient
                .call(messages, InsightGroupClassifyResponse.class)
                .map(response -> toGroupingResult(response, items, itemById, insightType))
                .switchIfEmpty(Mono.just(fallbackSingleGroup(items)))
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(2, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(10))
                                .doBeforeRetry(
                                        signal ->
                                                log.warn(
                                                        "Insight grouping failed, retrying for the"
                                                                + " {} time [type={}]: {}",
                                                        signal.totalRetries() + 1,
                                                        insightType.name(),
                                                        signal.failure().getMessage())))
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "Insight grouping ultimately failed, degrading to single group"
                                            + " [type={}]: {}",
                                    insightType.name(),
                                    e.getMessage());
                            return Mono.just(fallbackSingleGroup(items));
                        });
    }

    private Map<String, List<MemoryItem>> toGroupingResult(
            InsightGroupClassifyResponse response,
            List<MemoryItem> items,
            Map<String, MemoryItem> itemById,
            MemoryInsightType insightType) {
        if (response == null || response.assignments() == null) {
            return fallbackSingleGroup(items);
        }

        Map<String, List<MemoryItem>> result = new LinkedHashMap<>();
        for (var assignment : response.assignments()) {
            var groupItems = new ArrayList<MemoryItem>();
            for (var itemId : assignment.itemIds()) {
                var item = itemById.get(itemId);
                if (item != null) {
                    groupItems.add(item);
                }
            }
            if (!groupItems.isEmpty()) {
                var sanitizedGroupName =
                        sanitizeGroupName(assignment.groupName(), groupItems, insightType);
                result.computeIfAbsent(sanitizedGroupName, key -> new ArrayList<>())
                        .addAll(groupItems);
            }
        }

        var assigned =
                result.values().stream()
                        .flatMap(List::stream)
                        .map(MemoryItem::id)
                        .collect(Collectors.toSet());
        var unassignedCount = items.size() - assigned.size();
        if (unassignedCount > 0) {
            log.debug(
                    "After grouping, {} items were not assigned, remaining ungrouped [type={}]",
                    unassignedCount,
                    insightType.name());
        }

        return result;
    }

    private Map<String, List<MemoryItem>> fallbackSingleGroup(List<MemoryItem> items) {
        return Map.of("General", new ArrayList<>(items));
    }

    private String sanitizeGroupName(
            String rawGroupName, List<MemoryItem> groupItems, MemoryInsightType insightType) {
        var normalized = normalizeGroupName(rawGroupName);
        if (!isInvalidGroupName(normalized, insightType)) {
            return normalized;
        }
        var fallback = buildFallbackGroupName(groupItems);
        log.debug(
                "Replacing invalid insight group name [{}] with fallback [{}] [type={}]",
                rawGroupName,
                fallback,
                insightType.name());
        return fallback;
    }

    private String normalizeGroupName(String groupName) {
        if (groupName == null) {
            return "";
        }
        var normalized = groupName.trim();
        normalized = LEADING_METADATA.matcher(normalized).replaceFirst("");
        normalized = LEADING_DATE.matcher(normalized).replaceFirst("");
        normalized = normalized.replace('\"', ' ').replace('\'', ' ').trim();
        return WHITESPACE.matcher(normalized).replaceAll(" ").trim();
    }

    private boolean isInvalidGroupName(String groupName, MemoryInsightType insightType) {
        if (groupName.isBlank()) {
            return true;
        }
        if (groupName.equalsIgnoreCase(insightType.name())) {
            return true;
        }
        var normalizedLowerCase = groupName.toLowerCase();
        if (GENERIC_GROUP_NAMES.contains(normalizedLowerCase)) {
            return true;
        }
        return normalizedLowerCase.contains("会话记录")
                || normalizedLowerCase.contains("对话记录")
                || normalizedLowerCase.contains("聊天记录")
                || normalizedLowerCase.contains("session note")
                || normalizedLowerCase.contains("session record")
                || normalizedLowerCase.contains("chat record");
    }

    private String buildFallbackGroupName(List<MemoryItem> groupItems) {
        var candidates = new HashSet<String>();
        for (var item : groupItems) {
            var candidate = firstReadableClause(item.content());
            if (!candidate.isBlank()) {
                candidates.add(candidate);
            }
        }
        return candidates.stream()
                .sorted(
                        Comparator.<String>comparingInt(String::length)
                                .reversed()
                                .thenComparing(Comparator.naturalOrder()))
                .findFirst()
                .orElse("General");
    }

    private String firstReadableClause(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        var normalized = content.trim();
        normalized = LEADING_DATE.matcher(normalized).replaceFirst("");
        normalized = LEADING_SPEAKER.matcher(normalized).replaceFirst("");
        for (var rawClause : SPLIT_CLAUSE.split(normalized)) {
            var clause = normalizeClause(rawClause);
            if (!clause.isBlank()) {
                return clause;
            }
        }
        return normalizeClause(normalized);
    }

    private String normalizeClause(String clause) {
        var normalized = clause == null ? "" : clause.trim();
        normalized = normalized.replaceAll("^[\\[\\]【】()（）\"'“”‘’]+", "");
        normalized = normalized.replaceAll("[\\[\\]【】()（）\"'“”‘’]+$", "");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.contains(" ")) {
            var words = normalized.split(" ");
            if (words.length > 6) {
                normalized = String.join(" ", List.of(words).subList(0, 6));
            }
            return normalized;
        }
        var codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount <= 12) {
            return normalized;
        }
        var endIndex = normalized.offsetByCodePoints(0, 12);
        return normalized.substring(0, endIndex);
    }
}
