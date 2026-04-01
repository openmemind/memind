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
package com.openmemind.ai.memory.benchmark.locomo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.benchmark.core.dataset.Message;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LocomoDatasetLoader {

    private static final DateTimeFormatter LocomoTimestampFormatter =
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("h:mm a 'on' d MMMM, yyyy")
                    .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                    .toFormatter(Locale.ENGLISH);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocomoDataset load(Path path) {
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            List<LocomoDataset.LocomoUser> users = new ArrayList<>();
            int userIndex = 0;
            for (JsonNode item : root) {
                users.add(loadUser(item, userIndex));
                userIndex++;
            }
            return new LocomoDataset("locomo", List.copyOf(users));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load LoCoMo dataset from " + path, exception);
        }
    }

    private LocomoDataset.LocomoUser loadUser(JsonNode item, int userIndex) {
        JsonNode conversation = item.path("conversation");
        List<LocomoDataset.LocomoSession> sessions = loadSessions(conversation);
        List<LocomoDataset.LocomoQuestion> questions = loadQuestions(item.path("qa"), userIndex);
        return new LocomoDataset.LocomoUser(
                "locomo-" + userIndex,
                conversation.path("speaker_a").asText("Speaker A"),
                conversation.path("speaker_b").asText("Speaker B"),
                List.copyOf(sessions),
                List.copyOf(questions));
    }

    private List<LocomoDataset.LocomoSession> loadSessions(JsonNode conversation) {
        List<LocomoDataset.LocomoSession> sessions = new ArrayList<>();
        int sessionIndex = 1;
        while (conversation.has("session_" + sessionIndex)) {
            String dateTime =
                    conversation.path("session_" + sessionIndex + "_date_time").asText("");
            JsonNode sessionNode = conversation.path("session_" + sessionIndex);
            List<Message> messages = new ArrayList<>();
            Instant timestamp = parseTimestamp(dateTime);
            for (JsonNode messageNode : sessionNode) {
                messages.add(
                        new Message(
                                messageNode.path("speaker").asText(),
                                messageNode.path("text").asText(),
                                timestamp,
                                Map.of()));
            }
            sessions.add(
                    new LocomoDataset.LocomoSession(sessionIndex, dateTime, List.copyOf(messages)));
            sessionIndex++;
        }
        return sessions;
    }

    private List<LocomoDataset.LocomoQuestion> loadQuestions(JsonNode qaNode, int userIndex) {
        List<LocomoDataset.LocomoQuestion> questions = new ArrayList<>();
        int questionIndex = 1;
        for (JsonNode questionNode : qaNode) {
            questions.add(
                    new LocomoDataset.LocomoQuestion(
                            "locomo-" + userIndex + "-q" + questionIndex,
                            questionNode.path("question").asText(),
                            questionNode.path("answer").asText(),
                            questionNode.path("category").asInt()));
            questionIndex++;
        }
        return questions;
    }

    private Instant parseTimestamp(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(rawTimestamp, LocomoTimestampFormatter)
                .toInstant(ZoneOffset.UTC);
    }
}
