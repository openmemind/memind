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
package com.openmemind.ai.memory.core.prompt.extraction.rawdata;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Conversation segmentation prompt construction — unique entry point.
 *
 * <p>Constructs a system + user prompt for the LLM segmenter, formatting the message list into
 * indexed text. Each segment will be independently summarized into a narrative caption.
 */
public final class ConversationSegmentationPrompts {

    private static final int MAX_MESSAGES_PER_SEGMENT = 50;

    private static final String SYSTEM_PROMPT_TEMPLATE =
            """
            You are a conversation segmentation assistant. Your task is to divide a long \
            indexed conversation into coherent topical segments.

            Each segment will be independently summarized into a narrative caption. Therefore \
            each segment must represent ONE coherent topic or discussion that can be \
            meaningfully summarized as a single story.

            Core principle: **Prefer fewer, larger segments over many small ones.** \
            Over-segmentation fragments coherent discussions, making each segment too thin \
            to produce a useful summary. When unsure where to split, keep messages together.

            Return ONLY a JSON object with segment boundaries.

            # Workflow

            Follow these steps in order:

            1. **Read all messages** end-to-end. Identify the major topics discussed.
            2. **Find natural boundaries** between topics. A boundary is where one discussion \
            ends and a different one begins.
            3. **Verify constraints**: each segment has at least {{min_messages_per_segment}} \
            messages and at most {{max_messages_per_segment}} messages, segments are contiguous, \
            and all messages are covered.
            4. **Output** the JSON result.

            # Where to Split

            Split at these points:

            - **Clear topic change**: The conversation shifts to a completely different \
            subject (e.g., debugging a database issue → planning a team outing).
            - **Time jump**: Message timestamps show a significant gap (hours or cross-day), \
            indicating a new session.
            - **Natural conclusion + new start**: One discussion wraps up (e.g., "ok, that's \
            settled") and the next message begins a new topic.
            - **Segment approaching maximum size**: When a segment is nearing \
            {{max_messages_per_segment}} messages and the topic hasn't changed, find the \
            closest natural pause point (e.g., a sub-topic boundary, a brief summary, or \
            a Q&A completion) to split. Do NOT wait until exactly \
            {{max_messages_per_segment}} and cut arbitrarily.

            # Where NOT to Split

            Do NOT split at these points:

            - **Between a question and its answer**: A question and the response discussing \
            it MUST be in the same segment. Never put a question at the end of one segment \
            and the answer at the start of the next.
            - **Before a closing statement**: Messages like "got it, thanks", "that worked", \
            "migration done" are the conclusion of the current discussion. They belong in \
            the CURRENT segment, not the next one.
            - **Between sub-topics of the same discussion**: If the conversation moves from \
            "K8s deployment" to "writing the Dockerfile" to "configuring health probes", \
            this is ONE discussion about deployment, not three segments.
            - **At a brief tangent**: If the conversation briefly goes off-topic but returns \
            to the main subject within a few messages, keep it all in one segment.

            # Constraints

            1. **Contiguity**: Segments MUST be strictly contiguous and cover ALL messages. \
            No gaps, no overlaps.
            2. **Boundary values**:
               - First segment's `start` MUST be 0.
               - Last segment's `end` MUST be exactly {{total_messages}} (the total message count).
            3. **Minimum size**: Each segment MUST contain at least \
            {{min_messages_per_segment}} messages.
            4. **Maximum size**: Each segment MUST NOT exceed {{max_messages_per_segment}} \
            messages. If a single topic runs longer than {{max_messages_per_segment}} messages, \
            find the best sub-topic boundary within it to split.
            5. **Index definition**: `start` is INCLUSIVE, `end` is EXCLUSIVE. \
            (e.g., start: 0, end: 20 covers messages [0] through [19])

            # Output Format

            Return valid JSON ONLY. No markdown fences, no surrounding text.

            {
              "segments": [
                { "start": 0, "end": 20 },
                { "start": 20, "end": 50 }
              ]
            }

            # Examples

            ## Good Example: Clean split at topic boundary

            Conversation:
            [0]-[18]: User and assistant discuss Redis cache configuration for Spring Boot
            [19]: user: "Perfect, the cache is working now. Thanks!"
            [20]: user: "By the way, I need to set up CI/CD for this project. Can you help?"
            [21]-[34]: Discussion about GitHub Actions CI/CD pipeline setup

            Output:
            {
              "segments": [
                { "start": 0, "end": 20 },
                { "start": 20, "end": 35 }
              ]
            }

            Why this is good:
            - The closing message [19] stays with the Redis discussion it concludes
            - The split happens where the new topic (CI/CD) actually begins at [20]
            - Each segment is a complete, self-contained discussion

            ## Bad Example 1: Splitting between question and answer (WRONG)

            [14]: user: "How do I configure the health check endpoint?"
            --- segment boundary here ---
            [15]: assistant: "Add management.endpoint.health.show-details=always to..."

            -> Wrong: The question at [14] and the answer at [15] are part of the same \
            exchange. Splitting here puts a question without its answer in one segment \
            and an answer without its question in the next. Both segments lose context.

            ## Bad Example 2: Over-segmentation (WRONG)

            Conversation about deploying an app:
            [0]-[8]: Discuss Docker image building
            [9]-[16]: Discuss Kubernetes configuration
            [17]-[24]: Discuss monitoring and logging setup

            Output (WRONG):
            {
              "segments": [
                { "start": 0, "end": 9 },
                { "start": 9, "end": 17 },
                { "start": 17, "end": 25 }
              ]
            }

            -> Wrong: Docker, K8s config, and monitoring are all sub-topics of ONE deployment \
            discussion. Splitting them creates three fragments that each lack the full \
            deployment context. Keep them as a single segment.\
            """;

    private static final String USER_PROMPT_TEMPLATE =
            """
            Please segment the following conversation.
            (Total messages: {{total_messages}}, Valid indices: 0 to {{last_index}})

            # Conversation

            {{conversation}}\
            """;

    private ConversationSegmentationPrompts() {}

    /**
     * Build segmentation prompt
     *
     * @param messages Message list
     * @param minMessagesPerSegment Minimum number of messages per segment
     * @return PromptTemplate ready to be rendered with a language
     */
    public static PromptTemplate build(List<Message> messages, int minMessagesPerSegment) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Message list cannot be null or empty");
        }

        int totalMessages = messages.size();
        int lastIndex = totalMessages - 1;

        return PromptTemplate.builder("ConversationSegmentation")
                .section("system", SYSTEM_PROMPT_TEMPLATE)
                .userPrompt(USER_PROMPT_TEMPLATE)
                .variable("total_messages", String.valueOf(totalMessages))
                .variable("min_messages_per_segment", String.valueOf(minMessagesPerSegment))
                .variable("max_messages_per_segment", String.valueOf(MAX_MESSAGES_PER_SEGMENT))
                .variable("last_index", String.valueOf(lastIndex))
                .variable("conversation", formatIndexed(messages))
                .build();
    }

    /**
     * Format the message list into indexed text
     *
     * @param messages Message list
     * @return Formatted text, such as "[0] user: xxx\n[1] assistant: xxx"
     */
    static String formatIndexed(List<Message> messages) {
        return IntStream.range(0, messages.size())
                .mapToObj(
                        i -> {
                            Message msg = messages.get(i);
                            String role =
                                    msg.role() != null
                                            ? msg.role().name().toLowerCase()
                                            : "unknown";
                            return String.format("[%d] %s: %s", i, role, msg.textContent());
                        })
                .collect(Collectors.joining("\n"));
    }
}
