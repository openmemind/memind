package com.openmemind.ai.memory.core.extraction.rawdata.chunk;

import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Conversation Chunker
 *
 * <p>Chunk the conversation content by the number of messages.
 *
 */
public class ConversationChunker {

    /**
     * Chunk the message list
     *
     * @param messages Message list
     * @param config Chunking configuration
     * @return List of segments
     */
    public List<Segment> chunk(List<Message> messages, ConversationChunkingConfig config) {
        if (messages.isEmpty()) {
            return List.of();
        }

        int size = config.messagesPerChunk();
        List<Segment> segments = new ArrayList<>();

        for (int i = 0; i < messages.size(); i += size) {
            int end = Math.min(i + size, messages.size());
            List<Message> chunkMessages = messages.subList(i, end);

            String text = formatMessages(chunkMessages);
            MessageBoundary boundary = new MessageBoundary(i, end);

            segments.add(new Segment(text, null, boundary, Map.of("messages", chunkMessages)));
        }

        return segments;
    }

    private String formatMessages(List<Message> messages) {
        return messages.stream()
                .map(ConversationContent::formatMessage)
                .collect(Collectors.joining("\n"));
    }
}
