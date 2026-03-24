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

import com.openmemind.ai.memory.core.extraction.context.CommitDetectionContext;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Boundary detection prompt construction — unique entry point.
 *
 * <p>Splits the message buffer into conversation_history and new_messages, combined with contextual
 * time interval information, to determine whether to seal the conversation boundary.
 */
public final class BoundaryDetectionPrompts {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final String SYSTEM_PROMPT =
            """
            You are a conversation boundary detector. Your task is to determine whether the \
            newest message starts a new topic or continues the existing conversation.

            You will receive a Conversation History (accumulated messages) and New Messages \
            (the latest message). Decide whether to seal (segment) the conversation history \
            as a completed chunk.

            Core principle: **Default to NOT sealing.** Over-segmentation breaks coherent \
            topics into fragments, which is worse than under-segmentation. Only seal when \
            you see a clear boundary signal.

            # Decision Procedure

            Follow these steps IN ORDER. Stop at the first step that gives a clear answer.

            ## Step 1: Check Continuation Signals

            If ANY of the following is true → `should_seal: false`

            1. **Direct follow-up**: The new message responds to, references, clarifies, or \
            extends something discussed in the history. This includes sub-topics (e.g., \
            history discusses K8s deployment → new message asks about Dockerfile → this is \
            the same topic).
            2. **Closing statement**: The new message wraps up or summarizes the current \
            discussion. Examples: "got it, thanks", "done, migration complete", \
            "ok let's go with that approach", "perfect, I'll try that". These belong to \
            the CURRENT chunk, they are NOT the start of a new topic.
            3. **Insufficient information**: The new message is too short or ambiguous to \
            determine topic. Examples: "ok", "hmm", "嗯", "好的", "lol", emoji-only \
            messages, single-word replies, images/files without text. When in doubt, \
            do NOT seal.

            If any of the above matched, stop here. Output `should_seal: false`.

            ## Step 2: Check Boundary Signals

            If ANY of the following is true → `should_seal: true`

            1. **Cross-day gap** (highest priority): The time gap indicates the new message \
            is on a different calendar day than the last message in history. Always seal \
            regardless of topic continuity.
            2. **Long gap + unrelated content**: The time gap is more than 4 hours, AND the \
            new message introduces a topic with no connection to the history.
            3. **Clear topic switch**: The new message is about a completely different subject \
            with zero connection to the history (e.g., technical discussion → vacation \
            plans, cooking recipes → code debugging).
            4. **New session after substantive conversation**: The history contains substantive \
            discussion (not just greetings), and the new message is a greeting or opening \
            that signals a fresh start (e.g., "Hi, I have a question about...").

            Note: Transition phrases like "by the way", "另外", "moving on" are supporting \
            evidence for a topic switch, but only when the actual content also changes.

            ## Step 3: Default

            If neither Step 1 nor Step 2 gave a clear answer → `should_seal: false`. \
            When uncertain, do NOT seal.

            # Time Gap Interpretation

            The time gap between the last history message and the new message is provided \
            in the user prompt. Use it as follows:

            | Time Gap         | Interpretation                                        |
            |------------------|-------------------------------------------------------|
            | < 5 minutes      | Rapid exchange. Very unlikely to be a boundary.       |
            | 5 - 30 minutes   | Normal pace. Seal only on clear topic switch.         |
            | 30 min - 4 hours | Moderate pause. Topic switch becomes more likely.     |
            | > 4 hours        | Long pause. Seal unless new message clearly continues.|
            | Cross-day        | Always seal. No exceptions.                           |

            Time gap alone is never sufficient to seal (except cross-day). Always combine \
            with content analysis.

            # Output Format

            Return valid JSON ONLY. No markdown fences, no surrounding text.

            {
              "reasoning": "Brief explanation of which step triggered and why",
              "should_seal": boolean,
              "confidence": 0.0-1.0
            }

            # Examples

            ## Good Example 1: Closing statement → do NOT seal

            Conversation History:
            - user: I need to migrate the database from MySQL to PostgreSQL
            - assistant: Here's a step-by-step plan: 1) Export schema with pg_dump...
            - user: What about foreign key differences?
            - assistant: PostgreSQL handles FK constraints differently, you'll need to...

            New Messages:
            - user: Got it, migration is done. Thanks for the help!

            Output:
            {
              "reasoning": "Step 1 matched: closing statement wrapping up the migration discussion. This message belongs to the current chunk.",
              "should_seal": false,
              "confidence": 0.95
            }

            ## Good Example 2: Clear topic switch → seal

            Conversation History:
            - user: How do I configure Redis cache TTL in Spring Boot?
            - assistant: Set spring.cache.redis.time-to-live=600000 in application.yml...
            - user: Perfect, that works now.

            New Messages:
            - user: By the way, I need to take 3 days off next week for a trip to Tokyo

            Output:
            {
              "reasoning": "Step 2 matched: clear topic switch. History is about Redis cache configuration, new message is about personal vacation plans. No connection.",
              "should_seal": true,
              "confidence": 0.95
            }

            ## Good Example 3: Sub-topic continuation → do NOT seal

            Conversation History:
            - user: I'm deploying my Spring Boot app to Kubernetes
            - assistant: I recommend using Helm charts. Set resource limits to CPU: 500m...

            New Messages:
            - user: How should I write the Dockerfile for this?

            Output:
            {
              "reasoning": "Step 1 matched: direct follow-up. Dockerfile is a sub-topic of the K8s deployment discussion in history.",
              "should_seal": false,
              "confidence": 0.95
            }

            ## Bad Example: Sealing on a closing statement (WRONG)

            Conversation History:
            - user: Help me debug this NullPointerException in UserService
            - assistant: The issue is at line 42 where user.getProfile() can return null...
            - user: Found it, adding a null check fixed it.

            Output (WRONG):
            {
              "reasoning": "User resolved the issue, conversation is complete.",
              "should_seal": true,
              "confidence": 0.85
            }

            -> Wrong: "Found it, adding a null check fixed it" is the natural conclusion of \
            the debugging discussion. It belongs to the CURRENT chunk. A closing statement \
            is NOT a boundary signal. Only seal when the NEXT message starts a NEW topic.\
            """;

    private static final String USER_PROMPT_TEMPLATE =
            """
            {{time_gap_section}}# Conversation History

            {{conversation_history}}

            # New Messages

            {{new_messages}}\
            """;

    private BoundaryDetectionPrompts() {}

    /**
     * Construct boundary detection prompt
     *
     * @param buffer Message buffer (must contain at least 1 message)
     * @param context Detection context
     * @return PromptTemplate ready to be rendered with a language
     */
    public static PromptTemplate build(List<Message> buffer, CommitDetectionContext context) {
        if (buffer == null || buffer.isEmpty()) {
            throw new IllegalArgumentException("Message buffer cannot be null or empty");
        }

        int size = buffer.size();
        List<Message> history = size > 1 ? buffer.subList(0, size - 1) : List.of();
        List<Message> newMessages = buffer.subList(Math.max(0, size - 1), size);

        String timeGapSection =
                context.lastTimeGap() != null
                        ? "Time gap between last history message and new message: %s\n\n"
                                .formatted(formatDuration(context.lastTimeGap()))
                        : "";

        return PromptTemplate.builder("BoundaryDetection")
                .section("system", SYSTEM_PROMPT)
                .userPrompt(USER_PROMPT_TEMPLATE)
                .variable("time_gap_section", timeGapSection)
                .variable("conversation_history", formatMessages(history))
                .variable("new_messages", formatMessages(newMessages))
                .build();
    }

    /** Format the message list into text with timestamps and userName */
    static String formatMessages(List<Message> messages) {
        if (messages.isEmpty()) {
            return "(No history)";
        }
        return messages.stream()
                .map(
                        msg -> {
                            String speaker =
                                    (msg.userName() != null && !msg.userName().isBlank())
                                            ? msg.userName()
                                            : msg.role().name().toLowerCase();

                            return msg.timestamp() != null
                                    ? String.format(
                                            "- %s (%s): %s",
                                            speaker,
                                            TIMESTAMP_FORMATTER.format(msg.timestamp()),
                                            msg.textContent())
                                    : String.format("- %s: %s", speaker, msg.textContent());
                        })
                .collect(Collectors.joining("\n"));
    }

    /** Format Duration into a natural language time interval with semantic hints */
    static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + " seconds (immediate response)";
        }

        long minutes = duration.toMinutes();
        if (minutes < 60) {
            return minutes + " minutes (recent conversation)";
        }

        long hours = duration.toHours();
        if (hours < 24) {
            return hours + " hours (same day, significant pause)";
        }

        return duration.toDays() + " days (cross-day, likely new conversation)";
    }
}
