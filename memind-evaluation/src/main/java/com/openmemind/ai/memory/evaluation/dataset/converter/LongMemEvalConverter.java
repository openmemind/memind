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
        try {
            List<Map<String, Object>> items =
                    objectMapper.readValue(inputPath.toFile(), new TypeReference<>() {});

            Map<String, Object> result = new LinkedHashMap<>();
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = items.get(i);
                result.put("conv_" + i, convertItem(item));
            }

            Path outputFile = outputDir.resolve("longmemeval_converted.json");
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
        LocalDateTime dateTime = LocalDateTime.parse(raw, INPUT_FORMAT);
        String formatted = dateTime.format(OUTPUT_FORMAT);
        // Only convert AM/PM to lowercase, keep month case
        return formatted.replace("AM", "am").replace("PM", "pm");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertItem(Map<String, Object> item) {
        List<List<Map<String, String>>> haystackSessions =
                (List<List<Map<String, String>>>) item.get("haystack_sessions");
        List<String> haystackDates = (List<String>) item.get("haystack_dates");

        List<List<Map<String, String>>> conversation = new ArrayList<>();
        for (int i = 0; i < haystackSessions.size(); i++) {
            List<Map<String, String>> session = haystackSessions.get(i);
            String dateStr = haystackDates.get(i);
            String timestamp = convertTimestamp(dateStr);

            List<Map<String, String>> convertedSession = new ArrayList<>();
            for (Map<String, String> msg : session) {
                Map<String, String> converted = new LinkedHashMap<>();
                converted.put("role", mapRole(msg.get("role")));
                converted.put("content", msg.get("content"));
                converted.put("timestamp", timestamp);
                convertedSession.add(converted);
            }
            conversation.add(convertedSession);
        }

        // QA pair
        Map<String, String> qaPair = new LinkedHashMap<>();
        qaPair.put("question", (String) item.get("question"));
        qaPair.put("answer", (String) item.get("answer"));
        qaPair.put("category", (String) item.get("category"));

        Map<String, Object> convEntry = new LinkedHashMap<>();
        convEntry.put("person_1", "Speaker_A");
        convEntry.put("person_2", "Speaker_B");
        convEntry.put("conversation", conversation);
        convEntry.put("qa_pairs", List.of(qaPair));
        return convEntry;
    }

    private static String mapRole(String role) {
        return switch (role) {
            case "human", "user" -> "Speaker_A";
            default -> "Speaker_B";
        };
    }
}
