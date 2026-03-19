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
package com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Conversation message
 *
 * @param role Role (USER or ASSISTANT)
 * @param content List of message content blocks
 * @param timestamp Message timestamp (optional, null means unknown)
 * @param userName User name (optional, used to replace "User" during extraction)
 */
public record Message(Role role, List<ContentBlock> content, Instant timestamp, String userName) {

    /**
     * Backward compatible 3-parameter constructor (userName defaults to null)
     */
    public Message(Role role, List<ContentBlock> content, Instant timestamp) {
        this(role, content, timestamp, null);
    }

    /**
     * Message role
     */
    public enum Role {
        USER,
        ASSISTANT
    }

    /**
     * Concatenate the text content of all TextBlocks
     *
     * @return Concatenated plain text
     */
    public String textContent() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.stream()
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::getText)
                .collect(Collectors.joining());
    }

    /**
     * Create user message (without timestamp)
     *
     * @param content Message content
     * @return User message
     */
    public static Message user(String content) {
        return new Message(Role.USER, List.of(TextBlock.builder().text(content).build()), null);
    }

    /**
     * Create user message
     *
     * @param content Message content
     * @param timestamp Message timestamp
     * @return User message
     */
    public static Message user(String content, Instant timestamp) {
        return new Message(
                Role.USER, List.of(TextBlock.builder().text(content).build()), timestamp);
    }

    /**
     * Create user message (with user name)
     *
     * @param content Message content
     * @param timestamp Message timestamp
     * @param userName User name
     * @return User message
     */
    public static Message user(String content, Instant timestamp, String userName) {
        return new Message(
                Role.USER, List.of(TextBlock.builder().text(content).build()), timestamp, userName);
    }

    /**
     * Create user message (ContentBlock list)
     *
     * @param content List of content blocks
     * @return User message
     */
    public static Message user(List<ContentBlock> content) {
        return new Message(Role.USER, content, null);
    }

    /**
     * Create assistant message (without timestamp)
     *
     * @param content Message content
     * @return Assistant message
     */
    public static Message assistant(String content) {
        return new Message(
                Role.ASSISTANT, List.of(TextBlock.builder().text(content).build()), null);
    }

    /**
     * Create assistant message
     *
     * @param content Message content
     * @param timestamp Message timestamp
     * @return Assistant message
     */
    public static Message assistant(String content, Instant timestamp) {
        return new Message(
                Role.ASSISTANT, List.of(TextBlock.builder().text(content).build()), timestamp);
    }

    /**
     * Create assistant message (ContentBlock list)
     *
     * @param content List of content blocks
     * @return Assistant message
     */
    public static Message assistant(List<ContentBlock> content) {
        return new Message(Role.ASSISTANT, content, null);
    }

    /**
     * Return a new Message with the userName set
     *
     * @param userName User name
     * @return New Message
     */
    public Message withUserName(String userName) {
        return new Message(role, content, timestamp, userName);
    }
}
