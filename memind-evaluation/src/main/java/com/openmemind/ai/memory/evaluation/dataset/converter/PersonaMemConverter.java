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
package com.openmemind.ai.memory.evaluation.dataset.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Convert raw PersonaMem files into the map-root LoCoMo-compatible shape that memind-evaluation
 * already loads via {@code LoCoMoDatasetLoader}.
 */
@Component
public class PersonaMemConverter implements DatasetConverter {

    private static final Logger log = LoggerFactory.getLogger(PersonaMemConverter.class);
    private static final String QUESTIONS_FILE = "questions_32k.csv";
    private static final String CONTEXTS_FILE = "shared_contexts_32k.jsonl";
    private static final Pattern PERSONA_NAME_PATTERN = Pattern.compile("Name:\\s*([^\\n]+)");
    private static final Pattern OPTION_LABEL_PATTERN =
            Pattern.compile("^\\(([a-zA-Z])\\)\\s*(.*)$");
    private static final TypeReference<List<Map<String, Object>>> MESSAGE_LIST =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public PersonaMemConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceFormat() {
        return "personamem";
    }

    @Override
    public Path convert(Path inputPath, Path outputDir) {
        Path datasetDir = Files.isDirectory(inputPath) ? inputPath : inputPath.getParent();
        Path questionsPath = requireFile(datasetDir, QUESTIONS_FILE);
        Path contextsPath = requireFile(datasetDir, CONTEXTS_FILE);

        try {
            Map<String, List<Map<String, Object>>> contexts = loadContexts(contextsPath);
            Map<ContextKey, List<Map<String, String>>> groupedQuestions =
                    groupQuestions(questionsPath);

            Map<String, Object> result = new LinkedHashMap<>();
            int index = 0;
            for (Map.Entry<ContextKey, List<Map<String, String>>> entry :
                    groupedQuestions.entrySet()) {
                ContextKey key = entry.getKey();
                List<Map<String, Object>> fullContext = contexts.get(key.contextId());
                if (fullContext == null) {
                    throw new IllegalArgumentException(
                            "Missing shared_context_id in PersonaMem contexts: " + key.contextId());
                }
                result.put(
                        "conv_" + index,
                        convertGroup(fullContext, key.endIndex(), entry.getValue()));
                index++;
            }

            Files.createDirectories(outputDir);
            Path outputFile = outputDir.resolve("personamem_converted.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), result);
            log.info(
                    "PersonaMem conversion completed, total {} dialogues -> {}", index, outputFile);
            return outputFile;
        } catch (IOException e) {
            throw new UncheckedIOException("PersonaMem conversion failed: " + inputPath, e);
        }
    }

    private Path requireFile(Path datasetDir, String fileName) {
        Path file = datasetDir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Missing PersonaMem file: " + file);
        }
        return file;
    }

    private Map<String, List<Map<String, Object>>> loadContexts(Path contextsPath)
            throws IOException {
        Map<String, List<Map<String, Object>>> contexts = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(contextsPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(line);
                root.fields()
                        .forEachRemaining(
                                entry ->
                                        contexts.put(
                                                entry.getKey(),
                                                objectMapper.convertValue(
                                                        entry.getValue(), MESSAGE_LIST)));
            }
        }
        return contexts;
    }

    private Map<ContextKey, List<Map<String, String>>> groupQuestions(Path questionsPath)
            throws IOException {
        List<String> lines = Files.readAllLines(questionsPath);
        if (lines.isEmpty()) {
            return Map.of();
        }

        List<String> headers = splitCsvLine(lines.getFirst());
        Map<ContextKey, List<Map<String, String>>> grouped = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            List<String> values = splitCsvLine(line);
            Map<String, String> row = toRow(headers, values);
            ContextKey key =
                    new ContextKey(
                            row.get("shared_context_id"),
                            Integer.parseInt(row.get("end_index_in_shared_context")));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private Map<String, Object> convertGroup(
            List<Map<String, Object>> fullContext,
            int endIndex,
            List<Map<String, String>> questionList) {
        int sliceEnd = Math.min(endIndex + 1, fullContext.size());
        List<Map<String, Object>> contextMessages = fullContext.subList(0, sliceEnd);

        String person1 = extractPersonaName(contextMessages);
        String person2 = "Assistant";

        List<Map<String, Object>> session = new ArrayList<>();
        int dialogueIndex = 0;
        for (Map<String, Object> msg : contextMessages) {
            String role = String.valueOf(msg.getOrDefault("role", ""));
            if ("system".equals(role)) {
                continue;
            }

            Map<String, Object> converted = new LinkedHashMap<>();
            converted.put("speaker", "user".equals(role) ? person1 : person2);
            converted.put(
                    "text", cleanMessagePrefix(String.valueOf(msg.getOrDefault("content", ""))));
            converted.put("dia_id", "D0:" + dialogueIndex);
            session.add(converted);
            dialogueIndex++;
        }

        List<Map<String, Object>> qaPairs = new ArrayList<>();
        for (Map<String, String> row : questionList) {
            Map<String, Object> qa = new LinkedHashMap<>();
            String questionType = row.getOrDefault("question_type", "");
            qa.put("question_id", row.getOrDefault("question_id", ""));
            qa.put("question", row.getOrDefault("user_question_or_message", ""));
            qa.put("answer", row.getOrDefault("correct_answer", ""));
            qa.put("category", questionType);
            qa.put("question_type", questionType);
            qa.put("all_options", formatOptions(row.getOrDefault("all_options", "")));
            qa.put("topic", row.getOrDefault("topic", ""));
            qa.put("persona_id", row.getOrDefault("persona_id", ""));
            qa.put(
                    "context_length_in_tokens",
                    parseIntOrDefault(row.get("context_length_in_tokens"), 0));
            qa.put(
                    "distance_to_ref_in_tokens",
                    parseIntOrDefault(row.get("distance_to_ref_in_tokens"), 0));
            qaPairs.add(qa);
        }

        Map<String, Object> convEntry = new LinkedHashMap<>();
        convEntry.put("person_1", person1);
        convEntry.put("person_2", person2);
        convEntry.put("conversation", List.of(session));
        convEntry.put("qa_pairs", qaPairs);
        return convEntry;
    }

    private String extractPersonaName(List<Map<String, Object>> contextMessages) {
        if (contextMessages.isEmpty()) {
            return "User";
        }
        Object firstRole = contextMessages.getFirst().get("role");
        Object firstContent = contextMessages.getFirst().get("content");
        if (!"system".equals(String.valueOf(firstRole)) || firstContent == null) {
            return "User";
        }
        Matcher matcher = PERSONA_NAME_PATTERN.matcher(String.valueOf(firstContent));
        return matcher.find() ? matcher.group(1).trim() : "User";
    }

    private String cleanMessagePrefix(String text) {
        return text.replaceFirst("^(User|Assistant):\\s*", "").trim();
    }

    private String formatOptions(String rawOptions) {
        List<String> formatted = new ArrayList<>();
        for (String option : parseSerializedOptions(rawOptions)) {
            Matcher optionMatcher = OPTION_LABEL_PATTERN.matcher(option);
            if (optionMatcher.matches()) {
                formatted.add(
                        "("
                                + optionMatcher.group(1).toLowerCase()
                                + ") "
                                + optionMatcher.group(2).trim());
            } else if (!option.isBlank()) {
                formatted.add(option);
            }
        }
        if (formatted.isEmpty()) {
            return rawOptions;
        }
        return String.join("\n", formatted);
    }

    private List<String> parseSerializedOptions(String rawOptions) {
        String value = rawOptions == null ? "" : rawOptions.trim();
        if (value.isBlank()) {
            return List.of();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }

        List<String> options = new ArrayList<>();
        int index = 0;
        while (index < value.length()) {
            while (index < value.length()
                    && (Character.isWhitespace(value.charAt(index))
                            || value.charAt(index) == ',')) {
                index++;
            }
            if (index >= value.length()) {
                break;
            }

            char start = value.charAt(index);
            if (start != '\'' && start != '"') {
                int nextComma = value.indexOf(',', index);
                String option =
                        (nextComma >= 0
                                        ? value.substring(index, nextComma)
                                        : value.substring(index))
                                .trim();
                if (!option.isBlank()) {
                    options.add(option);
                }
                index = nextComma >= 0 ? nextComma + 1 : value.length();
                continue;
            }

            char quote = start;
            index++;
            StringBuilder current = new StringBuilder();
            while (index < value.length()) {
                char ch = value.charAt(index);
                if (ch == '\\' && index + 1 < value.length()) {
                    current.append(value.charAt(index + 1));
                    index += 2;
                    continue;
                }
                if (ch == quote) {
                    index++;
                    break;
                }
                current.append(ch);
                index++;
            }

            String option = current.toString().trim();
            if (!option.isBlank()) {
                options.add(option);
            }
        }
        return options;
    }

    private Map<String, String> toRow(List<String> headers, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String value = i < values.size() ? values.get(i) : "";
            row.put(headers.get(i), value);
        }
        return row;
    }

    private List<String> splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString());
        return values;
    }

    private int parseIntOrDefault(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private record ContextKey(String contextId, int endIndex) {}
}
