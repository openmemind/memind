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

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Explicit boundary detection input.
 *
 * <p>Represents the existing pending conversation history and the newly arrived messages that may
 * start a new segment.
 */
public record CommitDetectionInput(
        List<Message> history, List<Message> incomingMessages, CommitDetectionContext context) {

    public CommitDetectionInput {
        history = List.copyOf(Objects.requireNonNull(history, "history is required"));
        incomingMessages =
                List.copyOf(
                        Objects.requireNonNull(incomingMessages, "incomingMessages is required"));
        context = context == null ? CommitDetectionContext.empty() : context;
    }

    /**
     * Candidate message window if the incoming messages were appended to the existing history.
     */
    public List<Message> candidateWindow() {
        var candidate = new ArrayList<Message>(history.size() + incomingMessages.size());
        candidate.addAll(history);
        candidate.addAll(incomingMessages);
        return List.copyOf(candidate);
    }

    /**
     * Returns the explicit time gap from context when present, otherwise computes the gap between
     * the last history message and the first incoming message.
     */
    public Duration timeGap() {
        if (context.lastTimeGap() != null) {
            return context.lastTimeGap();
        }
        if (history.isEmpty() || incomingMessages.isEmpty()) {
            return null;
        }
        Instant previous = history.getLast().timestamp();
        Instant incoming = incomingMessages.getFirst().timestamp();
        return (previous == null || incoming == null) ? null : Duration.between(previous, incoming);
    }
}
