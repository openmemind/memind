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
package com.openmemind.ai.memory.core.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper utilities for building LLM message lists.
 */
public final class ChatMessages {

    private ChatMessages() {}

    /**
     * Create a message list with an optional system prompt and a user prompt.
     *
     * <p>Blank or null prompts are omitted; no blank messages are created.
     *
     * @param systemPrompt system prompt (optional)
     * @param userPrompt user prompt (optional)
     * @return message list
     */
    public static List<ChatMessage> systemUser(String systemPrompt, String userPrompt) {
        var messages = new ArrayList<ChatMessage>(2);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new ChatMessage(ChatRole.SYSTEM, systemPrompt));
        }
        if (userPrompt != null && !userPrompt.isBlank()) {
            messages.add(new ChatMessage(ChatRole.USER, userPrompt));
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException(
                    "systemPrompt and userPrompt must not both be blank");
        }
        return List.copyOf(messages);
    }
}
