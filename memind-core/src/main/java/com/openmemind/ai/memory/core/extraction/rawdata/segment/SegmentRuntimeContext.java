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
package com.openmemind.ai.memory.core.extraction.rawdata.segment;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.time.Instant;
import java.util.List;

/**
 * Runtime-only context derived from source conversation messages for the current extraction flow.
 *
 * @param startTime first timestamp seen in the segment
 * @param observedAt last timestamp seen in the segment
 * @param userName first non-blank user name seen in the segment
 * @param sourceClient first non-blank source client seen in the segment
 */
public record SegmentRuntimeContext(
        Instant startTime, Instant observedAt, String userName, String sourceClient) {

    public SegmentRuntimeContext(Instant startTime, Instant observedAt, String userName) {
        this(startTime, observedAt, userName, null);
    }

    public static SegmentRuntimeContext fromConversationMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        Instant startTime = null;
        Instant observedAt = null;
        String userName = null;
        String sourceClient = null;

        for (Message message : messages) {
            if (startTime == null && message.timestamp() != null) {
                startTime = message.timestamp();
            }
            if (message.role() == Message.Role.USER
                    && userName == null
                    && message.userName() != null
                    && !message.userName().isBlank()) {
                userName = message.userName();
            }
            if (sourceClient == null
                    && message.sourceClient() != null
                    && !message.sourceClient().isBlank()) {
                sourceClient = message.sourceClient();
            }
            if (message.timestamp() != null) {
                observedAt = message.timestamp();
            }
        }

        if (startTime == null && observedAt == null && userName == null && sourceClient == null) {
            return null;
        }
        return new SegmentRuntimeContext(startTime, observedAt, userName, sourceClient);
    }
}
