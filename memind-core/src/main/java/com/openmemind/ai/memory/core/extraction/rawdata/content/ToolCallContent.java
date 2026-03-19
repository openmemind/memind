package com.openmemind.ai.memory.core.extraction.rawdata.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import com.openmemind.ai.memory.core.utils.HashUtils;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool call raw content
 *
 */
public class ToolCallContent extends RawContent {

    @Override
    public String contentType() {
        return "TOOL_CALL";
    }

    private final List<ToolCallRecord> calls;

    @JsonCreator
    public ToolCallContent(@JsonProperty("calls") List<ToolCallRecord> calls) {
        this.calls = calls != null ? List.copyOf(calls) : List.of();
    }

    public List<ToolCallRecord> calls() {
        return calls;
    }

    @Override
    public String toContentString() {
        return calls.stream().map(ToolCallContent::formatCall).collect(Collectors.joining("\n\n"));
    }

    @Override
    public String getContentId() {
        var combined =
                calls.stream()
                        .map(r -> r.toolName() + ":" + r.input() + ":" + r.output())
                        .collect(Collectors.joining("|"));
        return HashUtils.sampledSha256(combined);
    }

    private static String formatCall(ToolCallRecord record) {
        var header =
                "[Tool Call] toolName=%s status=%s".formatted(record.toolName(), record.status());
        return """
        %s
        Input: %s
        Output: %s
        Duration: %dms  InputTokens: %d  OutputTokens: %d\
        """
                .formatted(
                        header,
                        record.input(),
                        truncate(record.output(), 500),
                        record.durationMs(),
                        record.inputTokens(),
                        record.outputTokens());
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
