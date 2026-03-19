package com.openmemind.ai.memory.example.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Load example test data from JSON files in the classpath.
 *
 * <p>The input/output fields in JSON are readable object structures, serialized as strings when loaded into ToolCallRecord;
 * contentHash and calledAt are automatically calculated by the loader, and do not need to be filled in the JSON.
 *
 */
@Component
public class ExampleDataLoader {

    private final ObjectMapper objectMapper;

    public ExampleDataLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Load a list of Messages from a JSON file.
     *
     * <p>JSON structure: {@code [{"role": "USER"|"ASSISTANT", "content": "..."}]}
     */
    public List<Message> loadMessages(String classpathLocation) {
        var nodes = readJsonArray(classpathLocation);
        var messages = new ArrayList<Message>(nodes.size());
        for (var node : nodes) {
            var role = node.get("role").asText();
            var content = node.get("content").asText();
            messages.add("USER".equals(role) ? Message.user(content) : Message.assistant(content));
        }
        return messages;
    }

    /**
     * Load a list of ToolCallRecords from a JSON file.
     *
     * <p>JSON structure: input/output are objects, calledAtOffsetSeconds is the offset in seconds from the current time (positive number = past).
     * contentHash and calledAt are calculated automatically, and do not need to be included in the JSON.
     */
    public List<ToolCallRecord> loadToolCalls(String classpathLocation) {
        var nodes = readJsonArray(classpathLocation);
        var now = Instant.now();
        var records = new ArrayList<ToolCallRecord>(nodes.size());
        for (var node : nodes) {
            var toolName = node.get("toolName").asText();
            var input = nodeToString(node.get("input"));
            var output = nodeToString(node.get("output"));
            var status = node.get("status").asText();
            var durationMs = node.get("durationMs").asLong();
            var inputTokens = node.get("inputTokens").asInt();
            var outputTokens = node.get("outputTokens").asInt();
            var offsetSeconds = node.path("calledAtOffsetSeconds").asLong(0);
            var calledAt = now.minusSeconds(offsetSeconds);
            var contentHash = ToolCallRecord.computeHash(toolName, input, output);
            records.add(
                    new ToolCallRecord(
                            toolName,
                            input,
                            output,
                            status,
                            durationMs,
                            inputTokens,
                            outputTokens,
                            contentHash,
                            calledAt));
        }
        return records;
    }

    private List<JsonNode> readJsonArray(String classpathLocation) {
        try {
            var resource = new ClassPathResource(classpathLocation);
            try (InputStream is = resource.getInputStream()) {
                var arrayNode = objectMapper.readTree(is);
                var result = new ArrayList<JsonNode>(arrayNode.size());
                arrayNode.forEach(result::add);
                return result;
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load example data from: " + classpathLocation, e);
        }
    }

    private String nodeToString(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON node", e);
        }
    }
}
