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
package com.openmemind.ai.memory.core.buffer;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link RecentConversationBuffer}.
 */
public class InMemoryRecentConversationBuffer implements RecentConversationBuffer {

    private static final int DEFAULT_RETENTION = 100;

    private final ConcurrentHashMap<String, Deque<Message>> buffers = new ConcurrentHashMap<>();
    private final int retention;

    public InMemoryRecentConversationBuffer() {
        this(DEFAULT_RETENTION);
    }

    public InMemoryRecentConversationBuffer(int retention) {
        if (retention <= 0) {
            throw new IllegalArgumentException("retention must be positive");
        }
        this.retention = retention;
    }

    @Override
    public void append(String sessionId, Message message) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(message, "message");

        buffers.compute(
                sessionId,
                (key, existing) -> {
                    Deque<Message> deque = existing != null ? existing : new ArrayDeque<>();
                    deque.addLast(message);
                    while (deque.size() > retention) {
                        deque.removeFirst();
                    }
                    return deque;
                });
    }

    @Override
    public List<Message> loadRecent(String sessionId, int limit) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        Deque<Message> deque = buffers.get(sessionId);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }

        List<Message> snapshot = new ArrayList<>(deque);
        int fromIndex = Math.max(snapshot.size() - limit, 0);
        return List.copyOf(snapshot.subList(fromIndex, snapshot.size()));
    }

    @Override
    public void clear(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        buffers.remove(sessionId);
    }
}
