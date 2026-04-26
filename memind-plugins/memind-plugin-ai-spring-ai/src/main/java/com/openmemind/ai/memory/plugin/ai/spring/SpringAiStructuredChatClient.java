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
package com.openmemind.ai.memory.plugin.ai.spring;

import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Mono;

/**
 * StructuredChatClient implementation backed by Spring AI ChatClient.
 */
public class SpringAiStructuredChatClient implements StructuredChatClient {

    private static final Pattern THINKING_TAG_PATTERN = Pattern.compile("(?is)<think>.*?</think>");

    private final ChatClient chatClient;

    public SpringAiStructuredChatClient(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    @Override
    public Mono<String> call(List<ChatMessage> messages) {
        return Mono.fromCallable(() -> applyMessages(messages).call().content());
    }

    @Override
    public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
        Objects.requireNonNull(responseType, "responseType must not be null");
        return Mono.fromCallable(
                () ->
                        parseStructuredContent(
                                applyMessages(messages).call().content(), responseType));
    }

    private ChatClient.ChatClientRequestSpec applyMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        var mapped = messages.stream().map(this::toChatMessage).toList();
        return chatClient.prompt().messages(mapped);
    }

    private Message toChatMessage(ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    static <T> T parseStructuredContent(String content, Class<T> responseType) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String payload = extractJsonPayload(content);
        return JsonUtils.fromJson(adaptTopLevelArrayPayload(payload, responseType), responseType);
    }

    private static <T> String adaptTopLevelArrayPayload(String payload, Class<T> responseType) {
        String trimmed = payload.trim();
        if (!trimmed.startsWith("[") || !isSingleListRecord(responseType)) {
            return payload;
        }
        String fieldName = responseType.getRecordComponents()[0].getName();
        return "{" + JsonUtils.toJson(fieldName) + ":" + trimmed + "}";
    }

    private static boolean isSingleListRecord(Class<?> responseType) {
        if (!responseType.isRecord()) {
            return false;
        }
        var components = responseType.getRecordComponents();
        return components.length == 1 && List.class.isAssignableFrom(components[0].getType());
    }

    private static String extractJsonPayload(String content) {
        String cleaned = THINKING_TAG_PATTERN.matcher(content.trim()).replaceAll("").trim();
        cleaned = stripMarkdownFence(cleaned);

        int objectStart = cleaned.indexOf('{');
        int arrayStart = cleaned.indexOf('[');
        int start = firstJsonStart(objectStart, arrayStart);
        if (start < 0) {
            return cleaned;
        }

        int end = findJsonEnd(cleaned, start);
        return cleaned.substring(start, end + 1).trim();
    }

    private static String stripMarkdownFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstLineEnd = content.indexOf('\n');
        int lastFence = content.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFence <= firstLineEnd) {
            return content;
        }
        return content.substring(firstLineEnd + 1, lastFence).trim();
    }

    private static int firstJsonStart(int objectStart, int arrayStart) {
        if (objectStart < 0) {
            return arrayStart;
        }
        if (arrayStart < 0) {
            return objectStart;
        }
        return Math.min(objectStart, arrayStart);
    }

    private static int findJsonEnd(String content, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{' || ch == '[') {
                depth++;
            } else if (ch == '}' || ch == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return content.length() - 1;
    }
}
