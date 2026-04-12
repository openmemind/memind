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
package com.openmemind.ai.memory.example.java.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.model.ToolCallRecord;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads shared example data from the repository-level data directory.
 */
public final class ExampleDataLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path dataRoot;

    public ExampleDataLoader() {
        this(new SharedExampleDataLocator().resolveExampleDataRoot());
    }

    public ExampleDataLoader(Path dataRoot) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot").toAbsolutePath().normalize();
    }

    public List<Message> loadMessages(String relativePath) {
        var nodes = readJsonArray(relativePath);
        var messages = new ArrayList<Message>(nodes.size());
        for (var node : nodes) {
            var role = node.get("role").asText();
            var content = node.get("content").asText();
            messages.add("USER".equals(role) ? Message.user(content) : Message.assistant(content));
        }
        return messages;
    }

    public List<ToolCallRecord> loadToolCalls(String relativePath) {
        var nodes = readJsonArray(relativePath);
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

    private List<JsonNode> readJsonArray(String relativePath) {
        Path dataFile = dataRoot.resolve(relativePath).normalize();
        if (!dataFile.startsWith(dataRoot)) {
            throw new IllegalArgumentException(
                    "Example data path escapes shared root: " + relativePath);
        }

        try (InputStream inputStream = Files.newInputStream(dataFile)) {
            var arrayNode = objectMapper.readTree(inputStream);
            var result = new ArrayList<JsonNode>(arrayNode.size());
            arrayNode.forEach(result::add);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load example data from: " + dataFile, e);
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
