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
package com.openmemind.ai.memory.server.service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.RetrievalOptions;
import com.openmemind.ai.memory.server.domain.config.view.MemoryOptionItemView;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class MemoryOptionsProjectionMapper {

    private static final Pattern CAMEL_BOUNDARY =
            Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

    private static final Map<String, String> DESCRIPTION_BY_KEY =
            Map.ofEntries(
                    Map.entry(
                            "extraction.common.defaultScope",
                            "Default memory scope used when extraction input does not specify user"
                                    + " or agent scope."),
                    Map.entry(
                            "extraction.common.timeout",
                            "Maximum time allowed for a single extraction request before it is"
                                    + " aborted."),
                    Map.entry(
                            "extraction.common.language",
                            "Preferred language used by extraction prompts when generating memory"
                                    + " content."),
                    Map.entry(
                            "extraction.rawdata.conversation.messagesPerChunk",
                            "Chunk size used by fixed-size conversation segmentation."),
                    Map.entry(
                            "extraction.rawdata.conversation.strategy",
                            "Conversation segmentation strategy used before raw data extraction."),
                    Map.entry(
                            "extraction.rawdata.conversation.minMessagesPerSegment",
                            "Minimum message count required before LLM-based segmentation can split"
                                    + " a conversation."),
                    Map.entry(
                            "extraction.rawdata.commitDetection.maxMessages",
                            "Conversation buffer is force-committed once this message limit is"
                                    + " reached."),
                    Map.entry(
                            "extraction.rawdata.commitDetection.maxTokens",
                            "Conversation buffer is force-committed once this token limit is"
                                    + " reached."),
                    Map.entry(
                            "extraction.rawdata.commitDetection.minMessagesForLlm",
                            "Minimum buffered messages required before LLM topic-shift commit"
                                    + " detection runs."),
                    Map.entry(
                            "extraction.rawdata.vectorBatchSize",
                            "Maximum number of raw data captions embedded in a single vector batch"
                                    + " during extraction."),
                    Map.entry(
                            "extraction.item.foresightEnabled",
                            "Whether extraction should synthesize foresight-style item memories."),
                    Map.entry(
                            "extraction.insight.enabled",
                            "Whether insight building is enabled during extraction."),
                    Map.entry(
                            "retrieval.common.cacheEnabled",
                            "Whether retrieval can reuse cached ranking artifacts for repeated"
                                    + " requests."),
                    Map.entry(
                            "retrieval.simple.timeout",
                            "Maximum time allowed for a simple retrieval request."),
                    Map.entry(
                            "retrieval.simple.insightTopK",
                            "Maximum number of insights returned by simple retrieval."),
                    Map.entry(
                            "retrieval.simple.itemTopK",
                            "Maximum number of item memories returned by simple retrieval."),
                    Map.entry(
                            "retrieval.simple.rawDataTopK",
                            "Maximum number of raw data records returned by simple retrieval."),
                    Map.entry(
                            "retrieval.simple.keywordSearchEnabled",
                            "Whether simple retrieval should blend keyword search results with"
                                    + " vector results."),
                    Map.entry(
                            "retrieval.deep.timeout",
                            "Maximum time allowed for a deep retrieval request."),
                    Map.entry(
                            "retrieval.deep.insightTopK",
                            "Maximum number of insights retained during deep retrieval."),
                    Map.entry(
                            "retrieval.deep.itemTopK",
                            "Maximum number of items retained during deep retrieval."),
                    Map.entry(
                            "retrieval.deep.rawDataEnabled",
                            "Whether deep retrieval is allowed to include raw data in the final"
                                    + " result."),
                    Map.entry(
                            "retrieval.deep.rawDataTopK",
                            "Maximum number of raw data records retained during deep retrieval."),
                    Map.entry(
                            "retrieval.deep.queryExpansion.maxExpandedQueries",
                            "Maximum number of expanded queries generated during deep retrieval."),
                    Map.entry(
                            "retrieval.deep.sufficiency.itemTopK",
                            "Number of item candidates inspected by the deep retrieval sufficiency"
                                    + " check."),
                    Map.entry(
                            "retrieval.advanced.rerank.mode",
                            "Reranking mode used when combining retrieval candidates."),
                    Map.entry(
                            "retrieval.advanced.rerank.topK",
                            "Maximum number of results kept after reranking."));

    private static final List<OptionDefinition> DEFINITIONS =
            discoverDefinitions(PersistedMemoryOptions.from(MemoryBuildOptions.defaults()));

    private static final Set<String> KNOWN_KEYS =
            DEFINITIONS.stream()
                    .map(OptionDefinition::key)
                    .collect(java.util.stream.Collectors.toSet());

    private final ObjectMapper objectMapper;

    public MemoryOptionsProjectionMapper() {
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public Map<String, List<MemoryOptionItemView>> toProjection(MemoryBuildOptions options) {
        Objects.requireNonNull(options, "options");
        Map<String, Object> currentValues = flattenValues(PersistedMemoryOptions.from(options));
        Map<String, Object> defaultValues =
                flattenValues(PersistedMemoryOptions.from(MemoryBuildOptions.defaults()));
        Map<String, List<MemoryOptionItemView>> grouped = new LinkedHashMap<>();
        for (OptionDefinition definition : DEFINITIONS) {
            grouped.computeIfAbsent(definition.group(), ignored -> new ArrayList<>())
                    .add(
                            new MemoryOptionItemView(
                                    definition.key(),
                                    toApiValue(currentValues.get(definition.key())),
                                    description(definition.key()),
                                    typeName(definition.type()),
                                    toApiValue(defaultValues.get(definition.key())),
                                    constraints(definition.type())));
        }
        return grouped;
    }

    public MemoryBuildOptions toOptions(Map<String, List<MemoryOptionItemView>> config) {
        Objects.requireNonNull(config, "config");
        Map<String, MemoryOptionItemView> byKey = collectItems(config.values());
        validateKeys(byKey.keySet());
        ObjectNode root =
                objectMapper.valueToTree(
                        PersistedMemoryOptions.from(MemoryBuildOptions.defaults()));
        for (OptionDefinition definition : DEFINITIONS) {
            MemoryOptionItemView item = byKey.get(definition.key());
            setPathValue(
                    root,
                    definition.pathSegments(),
                    objectMapper.valueToTree(parseValue(item.value(), definition.type())));
        }
        try {
            return objectMapper.treeToValue(root, PersistedMemoryOptions.class).toOptions();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid memory options payload", e);
        }
    }

    private static List<OptionDefinition> discoverDefinitions(Object value) {
        List<OptionDefinition> definitions = new ArrayList<>();
        collectDefinitions(value, "", "", definitions);
        return List.copyOf(definitions);
    }

    private static void collectDefinitions(
            Object value,
            String currentPath,
            String currentGroup,
            List<OptionDefinition> definitions) {
        if (value == null) {
            return;
        }
        Class<?> type = value.getClass();
        if (!type.isRecord()) {
            definitions.add(
                    new OptionDefinition(
                            currentPath,
                            currentGroup,
                            value.getClass(),
                            List.of(currentPath.split("\\."))));
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            Object componentValue = readComponent(value, component);
            String nextPath =
                    currentPath.isBlank()
                            ? component.getName()
                            : currentPath + "." + component.getName();
            String nextGroup = currentPath.isBlank() ? component.getName() : currentGroup;
            collectDefinitions(componentValue, nextPath, nextGroup, definitions);
        }
    }

    private static Map<String, Object> flattenValues(Object value) {
        Map<String, Object> flattened = new LinkedHashMap<>();
        collectValues(value, "", flattened);
        return flattened;
    }

    private static void collectValues(
            Object value, String currentPath, Map<String, Object> flattened) {
        if (value == null) {
            return;
        }
        Class<?> type = value.getClass();
        if (!type.isRecord()) {
            flattened.put(currentPath, value);
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            Object componentValue = readComponent(value, component);
            String nextPath =
                    currentPath.isBlank()
                            ? component.getName()
                            : currentPath + "." + component.getName();
            collectValues(componentValue, nextPath, flattened);
        }
    }

    private static Object readComponent(Object value, RecordComponent component) {
        try {
            return component.getAccessor().invoke(value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    "Failed to read record component " + component.getName(), e);
        }
    }

    private static Map<String, MemoryOptionItemView> collectItems(
            Collection<List<MemoryOptionItemView>> groups) {
        Map<String, MemoryOptionItemView> byKey = new LinkedHashMap<>();
        for (List<MemoryOptionItemView> group : groups) {
            for (MemoryOptionItemView item : group) {
                MemoryOptionItemView previous = byKey.putIfAbsent(item.key(), item);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate memory option key: " + item.key());
                }
            }
        }
        return byKey;
    }

    private static void validateKeys(Set<String> keys) {
        Set<String> missing = new LinkedHashSet<>(KNOWN_KEYS);
        missing.removeAll(keys);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing memory option keys: " + missing);
        }
        Set<String> extra = new LinkedHashSet<>(keys);
        extra.removeAll(KNOWN_KEYS);
        if (!extra.isEmpty()) {
            throw new IllegalArgumentException("Unknown memory option keys: " + extra);
        }
    }

    private static void setPathValue(
            ObjectNode root,
            List<String> pathSegments,
            com.fasterxml.jackson.databind.JsonNode valueNode) {
        ObjectNode current = root;
        for (int i = 0; i < pathSegments.size() - 1; i++) {
            current = (ObjectNode) current.get(pathSegments.get(i));
        }
        current.set(pathSegments.get(pathSegments.size() - 1), valueNode);
    }

    private static Object parseValue(Object rawValue, Class<?> rawType) {
        Class<?> type = boxedType(rawType);
        if (type == String.class) {
            return rawValue == null ? null : rawValue.toString();
        }
        if (type == Boolean.class) {
            if (rawValue instanceof Boolean value) {
                return value;
            }
            return Boolean.parseBoolean(String.valueOf(rawValue));
        }
        if (type == Integer.class) {
            if (rawValue instanceof Number value) {
                return value.intValue();
            }
            return Integer.parseInt(String.valueOf(rawValue));
        }
        if (type == Long.class) {
            if (rawValue instanceof Number value) {
                return value.longValue();
            }
            return Long.parseLong(String.valueOf(rawValue));
        }
        if (type == Double.class) {
            if (rawValue instanceof Number value) {
                return value.doubleValue();
            }
            return Double.parseDouble(String.valueOf(rawValue));
        }
        if (type == Duration.class) {
            if (rawValue instanceof Duration value) {
                return value;
            }
            return Duration.parse(String.valueOf(rawValue));
        }
        if (Enum.class.isAssignableFrom(type)) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<? extends Enum> enumType = (Class<? extends Enum>) type.asSubclass(Enum.class);
            Enum<?> value = Enum.valueOf(enumType, String.valueOf(rawValue));
            return value;
        }
        throw new IllegalArgumentException("Unsupported memory option type: " + type.getName());
    }

    private static Object toApiValue(Object value) {
        if (value instanceof Duration duration) {
            return duration.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value;
    }

    private static Map<String, Object> constraints(Class<?> rawType) {
        Class<?> type = boxedType(rawType);
        if (Enum.class.isAssignableFrom(type)) {
            return Map.of(
                    "allowedValues",
                    Arrays.stream(type.getEnumConstants()).map(Object::toString).toList());
        }
        if (type == Duration.class) {
            return Map.of("format", "iso-8601-duration");
        }
        return Map.of();
    }

    private static String typeName(Class<?> rawType) {
        Class<?> type = boxedType(rawType);
        if (type == Boolean.class) {
            return "boolean";
        }
        if (type == Integer.class || type == Long.class) {
            return "integer";
        }
        if (type == Double.class || type == Float.class) {
            return "number";
        }
        if (type == Duration.class) {
            return "duration";
        }
        if (Enum.class.isAssignableFrom(type)) {
            return "enum";
        }
        return "string";
    }

    private static String description(String key) {
        return DESCRIPTION_BY_KEY.getOrDefault(key, fallbackDescription(key));
    }

    private static String fallbackDescription(String key) {
        String[] segments = key.split("\\.");
        String field = humanize(segments[segments.length - 1]);
        String context =
                Arrays.stream(segments, 0, segments.length - 1)
                        .map(MemoryOptionsProjectionMapper::humanize)
                        .collect(java.util.stream.Collectors.joining(" "));
        return "Controls " + field + " for " + context + ".";
    }

    private static String humanize(String value) {
        return Arrays.stream(CAMEL_BOUNDARY.split(value))
                .map(segment -> segment.replace('_', ' '))
                .map(segment -> segment.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static Class<?> boxedType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private record OptionDefinition(
            String key, String group, Class<?> type, List<String> pathSegments) {}

    private record PersistedMemoryOptions(
            ExtractionOptions extraction, RetrievalOptions retrieval) {

        private static PersistedMemoryOptions from(MemoryBuildOptions options) {
            return new PersistedMemoryOptions(options.extraction(), options.retrieval());
        }

        private MemoryBuildOptions toOptions() {
            return MemoryBuildOptions.builder().extraction(extraction).retrieval(retrieval).build();
        }
    }
}
