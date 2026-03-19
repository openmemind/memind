package com.openmemind.ai.memory.core.extraction.streaming;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory buffer storage implementation
 *
 * <p>Used for testing and scenarios that do not require persistence. Data is lost after application restart.
 *
 */
public class InMemoryConversationBufferStore implements ConversationBufferStore {

    private final ConcurrentHashMap<String, List<Message>> buffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> messageCounts = new ConcurrentHashMap<>();

    @Override
    public void save(String sessionId, List<Message> buffer) {
        buffers.put(sessionId, new ArrayList<>(buffer));
    }

    @Override
    public List<Message> load(String sessionId) {
        List<Message> buffer = buffers.get(sessionId);
        return buffer != null ? new ArrayList<>(buffer) : List.of();
    }

    @Override
    public void clear(String sessionId) {
        buffers.remove(sessionId);
        messageCounts.remove(sessionId);
    }

    @Override
    public void saveMessageCount(String sessionId, int count) {
        messageCounts.put(sessionId, count);
    }

    @Override
    public int loadMessageCount(String sessionId) {
        return messageCounts.getOrDefault(sessionId, 0);
    }

    @Override
    public List<String> listActiveSessions(MemoryId memoryId) {
        return new ArrayList<>(buffers.keySet());
    }
}
