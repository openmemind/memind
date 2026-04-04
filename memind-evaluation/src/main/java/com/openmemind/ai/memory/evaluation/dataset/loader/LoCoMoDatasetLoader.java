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
package com.openmemind.ai.memory.evaluation.dataset.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.evaluation.dataset.DatasetLoadOptions;
import com.openmemind.ai.memory.evaluation.dataset.DatasetLoader;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalConversation;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalDataset;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalMessage;
import com.openmemind.ai.memory.evaluation.dataset.model.QAPair;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * LoCoMo dataset loader, parses multi-session conversations and QA pairs, supports three-level timestamp fallback and content truncation
 *
 */
@Component
public class LoCoMoDatasetLoader implements DatasetLoader {

    // "6:07 pm on 13 January, 2023"
    private static final DateTimeFormatter LOCOMO_TS =
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("h:mm a 'on' d MMMM, yyyy")
                    .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                    .toFormatter(Locale.ENGLISH);

    private final ObjectMapper mapper;

    public LoCoMoDatasetLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String datasetName() {
        return "locomo";
    }

    @Override
    public EvalDataset load(Path dataPath, DatasetLoadOptions options) {
        try {
            JsonNode root = mapper.readTree(dataPath.toFile());
            List<EvalConversation> conversations = new ArrayList<>();
            List<QAPair> qaPairs = new ArrayList<>();

            if (root.isArray()) {
                int idx = 0;
                for (JsonNode item : root) {
                    String convId = options.datasetName() + "_" + idx;
                    conversations.add(
                            parseLoCoMoConversation(convId, item, options.maxContentLength()));
                    qaPairs.addAll(parseQaPairs(convId, item.get("qa")));
                    idx++;
                }
            } else {
                root.fields()
                        .forEachRemaining(
                                entry -> {
                                    String convId = entry.getKey();
                                    JsonNode convNode = entry.getValue();
                                    conversations.add(
                                            parseConversation(
                                                    convId, convNode, options.maxContentLength()));
                                    JsonNode qaNode =
                                            convNode.has("qa_pairs")
                                                    ? convNode.get("qa_pairs")
                                                    : convNode.get("qa");
                                    qaPairs.addAll(parseQaPairs(convId, qaNode));
                                });
            }

            return new EvalDataset(options.datasetName(), conversations, qaPairs);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load LoCoMo dataset from " + dataPath, e);
        }
    }

    /**
     * Parse a single conversation from the original LoCoMo array format:
     * conversation is an object, containing speaker_a/b, session_N_date_time, session_N (message array)
     */
    private EvalConversation parseLoCoMoConversation(
            String convId, JsonNode item, Integer maxContentLength) {
        JsonNode convObj = item.get("conversation");
        List<EvalMessage> messages = new ArrayList<>();

        String speakerA =
                convObj != null && convObj.has("speaker_a")
                        ? convObj.get("speaker_a").asText()
                        : "Speaker_A";
        String speakerB =
                convObj != null && convObj.has("speaker_b")
                        ? convObj.get("speaker_b").asText()
                        : "Speaker_B";

        if (convObj != null) {
            int sessionIdx = 1;
            int globalMsgIdx = 0;
            while (true) {
                String sessionKey = "session_" + sessionIdx;
                if (!convObj.has(sessionKey)) {
                    break;
                }

                // session-level timestamp from session_N_date_time
                String dateTimeKey = sessionKey + "_date_time";
                Instant sessionBase = null;
                if (convObj.has(dateTimeKey)) {
                    sessionBase = parseTimestamp(convObj.get(dateTimeKey).asText());
                }

                JsonNode sessionArr = convObj.get(sessionKey);
                int sessionMsgIdx = 0;
                for (JsonNode msg : sessionArr) {
                    String speakerName =
                            msg.has("speaker") ? msg.get("speaker").asText() : speakerA;
                    String content = msg.has("text") ? msg.get("text").asText() : "";

                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("sessionIndex", sessionIdx);
                    if (msg.has("blip_caption")) {
                        String caption = msg.get("blip_caption").asText();
                        content = content + "\n[Image: " + caption + "]";
                        meta.put("blip_caption", caption);
                    }
                    content = truncateContent(content, maxContentLength);

                    Instant ts = resolveTimestamp(msg, sessionBase, sessionMsgIdx, globalMsgIdx);
                    messages.add(
                            new EvalMessage(
                                    buildSpeakerUserId(convId, speakerName),
                                    speakerName,
                                    content,
                                    ts,
                                    meta));
                    globalMsgIdx++;
                    sessionMsgIdx++;
                }
                sessionIdx++;
            }
        }

        Map<String, Object> convMeta = new LinkedHashMap<>();
        convMeta.put("speaker_a", speakerA);
        convMeta.put("speaker_b", speakerB);
        return new EvalConversation(convId, messages, convMeta);
    }

    private EvalConversation parseConversation(
            String convId, JsonNode convNode, Integer maxContentLength) {
        JsonNode sessions = convNode.get("conversation");
        List<EvalMessage> messages = new ArrayList<>();
        int globalMsgIdx = 0;

        String speakerA =
                convNode.has("person_1") ? convNode.get("person_1").asText() : "Speaker_A";
        String speakerB =
                convNode.has("person_2") ? convNode.get("person_2").asText() : "Speaker_B";

        if (sessions != null && sessions.isArray()) {
            int sessionIdx = 0;
            for (JsonNode session : sessions) {
                // Parse session-level base time
                Instant sessionBase = parseSessionBase(session);

                // session may be an array (direct message list) or object (containing messages
                // field)
                JsonNode msgArray;
                if (session.isArray()) {
                    msgArray = session;
                } else if (session.isObject() && session.has("messages")) {
                    msgArray = session.get("messages");
                } else {
                    msgArray = session;
                }

                int sessionMsgIdx = 0;
                for (JsonNode msg : msgArray) {
                    String speakerName =
                            msg.has("speaker") ? msg.get("speaker").asText() : speakerA;
                    String content = msg.has("text") ? msg.get("text").asText() : "";

                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("sessionIndex", sessionIdx);
                    if (msg.has("blip_caption")) {
                        String caption = msg.get("blip_caption").asText();
                        content = content + "\n[Image: " + caption + "]";
                        meta.put("blip_caption", caption);
                    }

                    // Truncate content
                    content = truncateContent(content, maxContentLength);

                    // 3-level timestamp fallback
                    Instant ts = resolveTimestamp(msg, sessionBase, sessionMsgIdx, globalMsgIdx);

                    messages.add(
                            new EvalMessage(
                                    buildSpeakerUserId(convId, speakerName),
                                    speakerName,
                                    content,
                                    ts,
                                    meta));
                    globalMsgIdx++;
                    sessionMsgIdx++;
                }
                sessionIdx++;
            }
        }

        Map<String, Object> convMeta = new LinkedHashMap<>();
        convMeta.put("speaker_a", speakerA);
        convMeta.put("speaker_b", speakerB);
        return new EvalConversation(convId, messages, convMeta);
    }

    /**
     * 3-level timestamp fallback:
     * 1. message-level timestamp
     * 2. session-level base + sessionMsgIdx * 30s
     * 3. global fake timestamp
     */
    private Instant resolveTimestamp(
            JsonNode msg, Instant sessionBase, int sessionMsgIdx, int globalMsgIdx) {
        // Level 1: message-level timestamp
        if (msg.has("timestamp") && !msg.get("timestamp").asText().isBlank()) {
            Instant parsed = parseTimestamp(msg.get("timestamp").asText());
            if (parsed != null) {
                return parsed;
            }
        }
        // Level 2: session-level base
        if (sessionBase != null) {
            return sessionBase.plusSeconds(sessionMsgIdx * 30L);
        }
        // Level 3: global fake
        return generateFakeTimestamp(globalMsgIdx);
    }

    /**
     * Parse the "date" or "start_time" field of the session node as the base time
     */
    private Instant parseSessionBase(JsonNode session) {
        if (session == null || !session.isObject()) {
            return null;
        }
        if (session.has("date")) {
            return parseTimestamp(session.get("date").asText());
        }
        if (session.has("start_time")) {
            return parseTimestamp(session.get("start_time").asText());
        }
        return null;
    }

    private List<QAPair> parseQaPairs(String convId, JsonNode qasNode) {
        List<QAPair> pairs = new ArrayList<>();
        if (qasNode == null || !qasNode.isArray()) {
            return pairs;
        }

        for (int i = 0; i < qasNode.size(); i++) {
            JsonNode qa = qasNode.get(i);
            String category = qa.has("category") ? qa.get("category").asText() : "unknown";
            Map<String, Object> meta = new LinkedHashMap<>();
            String qId =
                    qa.has("question_id") ? qa.get("question_id").asText() : convId + "_qa" + i;
            meta.put("conversation_id", convId);
            meta.put("question_id", qId);
            if (qa.has("all_options")) {
                meta.put("all_options", qa.get("all_options").asText());
            }
            if (qa.has("question_type")) {
                meta.put("question_type", qa.get("question_type").asText());
            }
            if (qa.has("question_date")) {
                meta.put("question_date", qa.get("question_date").asText());
            }
            if (qa.has("haystack_dates")) {
                meta.put("haystack_dates", toTextList(qa.get("haystack_dates")));
            }
            if (qa.has("answer_session_ids")) {
                meta.put("answer_session_ids", toTextList(qa.get("answer_session_ids")));
            }
            List<String> evidence = new ArrayList<>();
            if (qa.has("evidence") && qa.get("evidence").isArray()) {
                qa.get("evidence").forEach(e -> evidence.add(e.asText()));
            }
            pairs.add(
                    new QAPair(
                            qId,
                            convId,
                            qa.has("question") ? qa.get("question").asText() : "",
                            qa.has("answer") ? qa.get("answer").asText() : "",
                            category,
                            evidence,
                            meta));
        }
        return pairs;
    }

    private List<String> toTextList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    // package-private for testing
    Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(raw.trim(), LOCOMO_TS);
            return ldt.atOffset(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    // package-private for testing
    String truncateContent(String content, Integer maxLength) {
        if (maxLength == null || content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength);
    }

    // package-private for testing
    Instant generateFakeTimestamp(int msgIndex) {
        return Instant.parse("2024-01-01T00:00:00Z").plusSeconds(msgIndex * 30L);
    }

    String buildSpeakerUserId(String conversationId, String speakerName) {
        return conversationId + "_" + speakerName.replace(" ", "_");
    }
}
