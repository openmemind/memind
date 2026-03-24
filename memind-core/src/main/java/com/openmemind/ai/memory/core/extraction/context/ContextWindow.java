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
package com.openmemind.ai.memory.core.extraction.context;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An assembled context window ready for LLM consumption.
 *
 * <p>Contains the recent messages from the conversation buffer and optionally
 * retrieved memories. Use {@link #formattedContext()} to get a single string
 * that can be injected into an LLM prompt.
 *
 * @param recentMessages messages currently in the conversation buffer
 * @param memories       retrieved memories relevant to the recent conversation;
 *                       {@code null} when memory retrieval was not requested
 * @param totalTokens    estimated total token count of the context window
 */
public record ContextWindow(
        List<Message> recentMessages, RetrievalResult memories, int totalTokens) {

    /**
     * Creates a buffer-only context window without retrieved memories.
     */
    public static ContextWindow bufferOnly(List<Message> recentMessages, int totalTokens) {
        return new ContextWindow(recentMessages, null, totalTokens);
    }

    /**
     * Returns {@code true} when retrieved memories are present and non-empty.
     */
    @JsonIgnore
    public boolean hasMemories() {
        return memories != null && !memories.isEmpty();
    }

    /**
     * Formats the context window into a single string suitable for LLM prompt injection.
     *
     * <p>The output combines retrieved memories (if any) followed by the recent
     * conversation messages.
     *
     * @return a formatted context string
     */
    @JsonIgnore
    public String formattedContext() {
        var sb = new StringBuilder();

        if (hasMemories()) {
            sb.append("<related-memories>\n");
            sb.append(memories.formattedResult());
            sb.append("\n</related-memories>\n\n");
        }

        if (recentMessages != null && !recentMessages.isEmpty()) {
            sb.append("<recent-messages>\n");
            sb.append(
                    recentMessages.stream()
                            .map(m -> "[" + m.role().name() + "] " + m.textContent())
                            .collect(Collectors.joining("\n")));
            sb.append("\n</recent-messages>");
        }

        return sb.toString().trim();
    }
}
