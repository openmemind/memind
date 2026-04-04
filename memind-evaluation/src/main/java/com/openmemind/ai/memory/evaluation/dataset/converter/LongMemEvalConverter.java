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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Convert LongMemEval format dataset to LoCoMo JSON format
 *
 */
@Component
public class LongMemEvalConverter implements DatasetConverter {
    private static final String DEFAULT_RAW_FILENAME = "longmemeval_s_cleaned.json";

    private static final Logger log = LoggerFactory.getLogger(LongMemEvalConverter.class);

    private static final DateTimeFormatter INPUT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd '('EEE')' HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter OUTPUT_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a 'on' d MMMM, yyyy", Locale.ENGLISH);

    private final ObjectMapper objectMapper;

    public LongMemEvalConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceFormat() {
        return "longmemeval";
    }

    @Override
    public Path convert(Path inputPath, Path outputDir) {
        Path sourcePath = resolveInputFile(inputPath);
        try {
            List<Map<String, Object>> items =
                    objectMapper.readValue(sourcePath.toFile(), new TypeReference<>() {});

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> item : items) {
                result.add(convertItem(item));
            }
            Path outputFile = outputDir.resolve("longmemeval_converted.json");
            Files.createDirectories(outputDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), result);
            log.info(
                    "LongMemEval conversion completed, total {} dialogues -> {}",
                    items.size(),
                    outputFile);
            return outputFile;
        } catch (IOException e) {
            throw new UncheckedIOException("LongMemEval conversion failed: " + inputPath, e);
        }
    }

    /**
     * Convert LongMemEval time format to LoCoMo format.
     *
     * @param raw The original time string, such as "2023/01/13 (Fri) 18:07"
     * @return LoCoMo format time, such as "6:07 pm on 13 January, 2023"
     */
    String convertTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        LocalDateTime dateTime = LocalDateTime.parse(raw, INPUT_FORMAT);
        String formatted = dateTime.format(OUTPUT_FORMAT);
        // Only convert AM/PM to lowercase, keep month case
        return formatted.replace("AM", "am").replace("PM", "pm");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertItem(Map<String, Object> item) {
        String questionId = stringValue(item, "question_id", "questionId");
        String question = stringValue(item, "question");
        String answer = stringValue(item, "answer");
        String questionType = stringValue(item, "question_type", "questionType", "category");
        String questionDate = stringValue(item, "question_date", "questionDate");
        List<String> haystackDates = stringList(item, "haystack_dates", "haystackDates");
        List<String> haystackSessionIds =
                stringList(item, "haystack_session_ids", "haystackSessionIds");
        List<String> answerSessionIds = stringList(item, "answer_session_ids", "answerSessionIds");
        List<List<Map<String, Object>>> haystackSessions =
                (List<List<Map<String, Object>>>) item.get("haystack_sessions");

        Map<String, Object> conversation = new LinkedHashMap<>();
        conversation.put("speaker_a", "user_" + questionId);
        conversation.put("speaker_b", "assistant_" + questionId);
        List<String> evidence = new ArrayList<>();
        for (int i = 0; i < haystackSessions.size(); i++) {
            List<Map<String, Object>> session = haystackSessions.get(i);
            String sessionId = i < haystackSessionIds.size() ? haystackSessionIds.get(i) : "";
            String dateStr = i < haystackDates.size() ? haystackDates.get(i) : "";
            conversation.put("session_" + (i + 1) + "_date_time", convertTimestamp(dateStr));

            List<Map<String, Object>> convertedSession = new ArrayList<>();
            for (int j = 0; j < session.size(); j++) {
                Map<String, Object> msg = session.get(j);
                String diaId = "D" + i + ":" + j;
                Map<String, Object> converted = new LinkedHashMap<>();
                converted.put("speaker", mapRole(stringValue(msg, "role"), questionId));
                converted.put("text", stringValue(msg, "content", "text"));
                converted.put("dia_id", diaId);
                convertedSession.add(converted);
                if (!sessionId.isBlank() && answerSessionIds.contains(sessionId)) {
                    evidence.add(diaId);
                }
            }
            conversation.put("session_" + (i + 1), convertedSession);
        }

        Map<String, Object> qaPair = new LinkedHashMap<>();
        qaPair.put("question_id", questionId);
        qaPair.put("question", question);
        qaPair.put("answer", answer);
        qaPair.put("category", questionType);
        qaPair.put("question_type", questionType);
        qaPair.put("question_date", questionDate);
        qaPair.put("haystack_dates", haystackDates);
        qaPair.put("answer_session_ids", answerSessionIds);
        qaPair.put("evidence", evidence);

        Map<String, Object> convEntry = new LinkedHashMap<>();
        convEntry.put("conversation", conversation);
        convEntry.put("qa", List.of(qaPair));
        return convEntry;
    }

    private String stringValue(Map<String, ?> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value instanceof String str) {
                return str;
            }
            if (value instanceof Number || value instanceof Boolean) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Map<String, ?> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value instanceof List<?> values) {
                return values.stream().map(String::valueOf).toList();
            }
        }
        return List.of();
    }

    private static String mapRole(String role, String questionId) {
        return switch (role) {
            case "human", "user" -> "user_" + questionId;
            default -> "assistant_" + questionId;
        };
    }

    private Path resolveInputFile(Path inputPath) {
        if (Files.isDirectory(inputPath)) {
            return inputPath.resolve(DEFAULT_RAW_FILENAME);
        }
        return inputPath;
    }
}
