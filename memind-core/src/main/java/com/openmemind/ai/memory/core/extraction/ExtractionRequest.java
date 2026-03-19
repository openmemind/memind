package com.openmemind.ai.memory.core.extraction;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ToolCallContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.Map;

/**
 * Memory extraction request
 *
 * @param memoryId Memory identifier
 * @param content Raw content
 * @param contentType Content type identifier (e.g. ContentTypes.CONVERSATION)
 * @param metadata Metadata
 * @param config Extraction configuration
 */
public record ExtractionRequest(
        MemoryId memoryId,
        RawContent content,
        String contentType,
        Map<String, Object> metadata,
        ExtractionConfig config) {

    /**
     * Create conversation extraction request
     */
    public static ExtractionRequest conversation(MemoryId memoryId, ConversationContent content) {
        return new ExtractionRequest(
                memoryId,
                content,
                ContentTypes.CONVERSATION,
                Map.of(),
                ExtractionConfig.defaults());
    }

    /**
     * Create text extraction request
     */
    public static ExtractionRequest text(MemoryId memoryId, String text) {
        var content = new ConversationContent(java.util.List.of(Message.user(text)));
        return new ExtractionRequest(
                memoryId,
                content,
                ContentTypes.CONVERSATION,
                Map.of(),
                ExtractionConfig.defaults());
    }

    /**
     * Create extraction request from arbitrary {@link RawContent}.
     *
     * <p>The content type is derived from {@link RawContent#contentType()}.
     */
    public static ExtractionRequest of(MemoryId memoryId, RawContent content) {
        return new ExtractionRequest(
                memoryId, content, content.contentType(), Map.of(), ExtractionConfig.defaults());
    }

    /**
     * Create tool call extraction request
     */
    public static ExtractionRequest toolCall(MemoryId memoryId, ToolCallContent content) {
        return new ExtractionRequest(
                memoryId, content, ContentTypes.TOOL_CALL, Map.of(), ExtractionConfig.agentOnly());
    }

    /**
     * Modify configuration
     */
    public ExtractionRequest withConfig(ExtractionConfig config) {
        return new ExtractionRequest(memoryId, content, contentType, metadata, config);
    }

    /**
     * Disable Insight generation
     */
    public ExtractionRequest withoutInsight() {
        return withConfig(config.withEnableInsight(false));
    }

    /**
     * Add metadata
     */
    public ExtractionRequest withMetadata(String key, Object value) {
        var newMetadata = new java.util.HashMap<>(metadata);
        newMetadata.put(key, value);
        return new ExtractionRequest(
                memoryId, content, contentType, Map.copyOf(newMetadata), config);
    }
}
