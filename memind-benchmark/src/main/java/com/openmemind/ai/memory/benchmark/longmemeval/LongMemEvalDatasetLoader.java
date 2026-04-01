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
package com.openmemind.ai.memory.benchmark.longmemeval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.benchmark.core.dataset.Message;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LongMemEvalDatasetLoader {

    private static final DateTimeFormatter LongMemEvalTimestampFormatter =
            DateTimeFormatter.ofPattern("yyyy/MM/dd '('EEE')' HH:mm", Locale.ENGLISH);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public LongMemEvalDataset load(Path path) {
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            List<LongMemEvalDataset.LongMemEvalQuestion> questions = new ArrayList<>();
            int questionIndex = 1;
            for (JsonNode item : root) {
                questions.add(loadQuestion(item, questionIndex));
                questionIndex++;
            }
            return new LongMemEvalDataset("longmemeval", List.copyOf(questions));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load LongMemEval dataset from " + path, exception);
        }
    }

    private LongMemEvalDataset.LongMemEvalQuestion loadQuestion(JsonNode item, int questionIndex) {
        List<String> haystackDates = loadHaystackDates(item);
        return new LongMemEvalDataset.LongMemEvalQuestion(
                text(item, "questionId", "question_id", "id", "question_id_string")
                        .filter(value -> !value.isBlank())
                        .orElse("longmemeval-q" + questionIndex),
                text(item, "question").orElse(""),
                text(item, "answer").orElse(""),
                text(item, "questionType", "question_type", "category").orElse("default"),
                text(item, "questionDate", "question_date").orElse(""),
                haystackDates,
                loadSessions(item, haystackDates));
    }

    private List<String> loadHaystackDates(JsonNode item) {
        JsonNode datesNode = firstPresent(item, "haystackDates", "haystack_dates");
        if (datesNode == null || !datesNode.isArray()) {
            return List.of();
        }
        List<String> haystackDates = new ArrayList<>();
        for (JsonNode dateNode : datesNode) {
            haystackDates.add(dateNode.asText(""));
        }
        return List.copyOf(haystackDates);
    }

    private List<List<Message>> loadSessions(JsonNode item, List<String> haystackDates) {
        JsonNode sessionsNode = firstPresent(item, "haystackSessions", "haystack_sessions");
        if (sessionsNode == null || !sessionsNode.isArray()) {
            return List.of();
        }

        List<List<Message>> sessions = new ArrayList<>();
        for (int sessionIndex = 0; sessionIndex < sessionsNode.size(); sessionIndex++) {
            JsonNode sessionNode = sessionsNode.get(sessionIndex);
            String sessionDate =
                    sessionIndex < haystackDates.size() ? haystackDates.get(sessionIndex) : null;
            sessions.add(loadSession(sessionNode, sessionDate));
        }
        return List.copyOf(sessions);
    }

    private List<Message> loadSession(JsonNode sessionNode, String sessionDate) {
        if (sessionNode == null || !sessionNode.isArray()) {
            return List.of();
        }

        Instant sessionTimestamp = parseTimestamp(sessionDate);
        List<Message> messages = new ArrayList<>();
        for (JsonNode messageNode : sessionNode) {
            String speaker = text(messageNode, "speaker", "role").orElse("assistant");
            String text = text(messageNode, "text", "content").orElse("");
            Instant timestamp =
                    text(messageNode, "timestamp")
                            .map(this::parseTimestamp)
                            .orElse(sessionTimestamp);
            messages.add(new Message(speaker, text, timestamp, Map.of()));
        }
        return List.copyOf(messages);
    }

    private Instant parseTimestamp(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(rawValue);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(rawValue).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(rawValue, LongMemEvalTimestampFormatter)
                    .toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        return null;
    }

    private java.util.Optional<String> text(JsonNode node, String... fieldNames) {
        JsonNode field = firstPresent(node, fieldNames);
        if (field == null || field.isNull()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(field.asText(""));
    }

    private JsonNode firstPresent(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                return node.get(fieldName);
            }
        }
        return null;
    }
}
