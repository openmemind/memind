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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory buffer storage implementation
 *
 * <p>Used for testing and scenarios that do not require persistence. Data is lost after application restart.
 *
 */
public class InMemoryConversationBuffer implements PendingConversationBuffer {

    private final ConcurrentHashMap<String, List<Message>> buffers = new ConcurrentHashMap<>();

    @Override
    public void append(String sessionId, Message message) {
        buffers.compute(
                sessionId,
                (ignored, existing) -> {
                    List<Message> next =
                            existing != null ? new ArrayList<>(existing) : new ArrayList<>();
                    next.add(message);
                    return next;
                });
    }

    @Override
    public List<Message> load(String sessionId) {
        List<Message> buffer = buffers.get(sessionId);
        return buffer != null ? new ArrayList<>(buffer) : List.of();
    }

    @Override
    public void clear(String sessionId) {
        buffers.remove(sessionId);
    }

    @Override
    public List<Message> drain(String sessionId) {
        List<Message> removed = buffers.remove(sessionId);
        return removed != null ? removed : List.of();
    }
}
