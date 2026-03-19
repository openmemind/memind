package com.openmemind.ai.memory.evaluation.dataset.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Convert PersonaMem format dataset to LoCoMo JSON format
 *
 */
@Component
public class PersonaMemConverter implements DatasetConverter {

    private static final Logger log = LoggerFactory.getLogger(PersonaMemConverter.class);

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
        try {
            List<Map<String, Object>> items =
                    objectMapper.readValue(inputPath.toFile(), new TypeReference<>() {});

            Map<String, Object> result = new LinkedHashMap<>();
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = items.get(i);
                result.put("conv_" + i, convertItem(item));
            }

            Path outputFile = outputDir.resolve("personamem_converted.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), result);
            log.info(
                    "PersonaMem conversion completed, total {} dialogues -> {}",
                    items.size(),
                    outputFile);
            return outputFile;
        } catch (IOException e) {
            throw new UncheckedIOException("PersonaMem conversion failed: " + inputPath, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertItem(Map<String, Object> item) {
        List<Map<String, String>> conversations =
                (List<Map<String, String>>) item.get("conversations");
        List<Map<String, String>> qaList = (List<Map<String, String>>) item.get("qa");

        // Treat all messages as a single session
        List<Map<String, String>> session = new ArrayList<>();
        for (Map<String, String> msg : conversations) {
            Map<String, String> converted = new LinkedHashMap<>();
            converted.put("role", mapRole(msg.get("role")));
            converted.put("content", msg.get("content"));
            session.add(converted);
        }

        // QA pairs
        List<Map<String, String>> qaPairs = new ArrayList<>();
        for (Map<String, String> qa : qaList) {
            Map<String, String> qaPair = new LinkedHashMap<>();
            qaPair.put("question", qa.get("question"));
            qaPair.put("answer", qa.get("answer"));
            qaPair.put("category", qa.get("category"));
            qaPairs.add(qaPair);
        }

        Map<String, Object> convEntry = new LinkedHashMap<>();
        convEntry.put("person_1", "User");
        convEntry.put("person_2", "Assistant");
        convEntry.put("conversation", List.of(session));
        convEntry.put("qa_pairs", qaPairs);
        return convEntry;
    }

    private static String mapRole(String role) {
        return "user".equals(role) ? "User" : "Assistant";
    }
}
