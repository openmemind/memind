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
package com.openmemind.ai.memory.core.store.buffer;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.List;

/**
 * Stream buffer persistent storage
 *
 * <p>Persist the message buffer of the ConversationSession to prevent loss of unarchived messages due to application crashes.
 *
 */
public interface ConversationBuffer {

    /**
     * Save buffer (overwrite)
     *
     * @param sessionId Session identifier
     * @param buffer Current message list
     */
    void save(String sessionId, List<Message> buffer);

    /**
     * Load buffer
     *
     * @param sessionId Session identifier
     * @return Message list, returns an empty list if not found
     */
    List<Message> load(String sessionId);

    /**
     * Clear buffer
     *
     * @param sessionId Session identifier
     */
    void clear(String sessionId);

    /**
     * Atomically load and clear buffer.
     *
     * <p>Returns all buffered messages and removes them in a single atomic operation,
     * preventing concurrent commits from losing or duplicating messages.
     *
     * @param sessionId Session identifier
     * @return Message list, returns an empty list if not found
     */
    default List<Message> drain(String sessionId) {
        List<Message> messages = load(sessionId);
        clear(sessionId);
        return messages;
    }

    /**
     * List all active sessions under the specified memory
     *
     * @param memoryId Memory identifier
     * @return List of active session IDs
     */
    List<String> listActiveSessions(MemoryId memoryId);

    /**
     * Save the total number of processed messages (used to calculate the absolute message position across segments)
     *
     * @param sessionId Session identifier
     * @param count Total number of processed messages
     */
    default void saveMessageCount(String sessionId, int count) {}

    /**
     * Load the total number of processed messages
     *
     * @param sessionId Session identifier
     * @return Total number of processed messages, returns 0 if not found
     */
    default int loadMessageCount(String sessionId) {
        return 0;
    }
}
