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
package com.openmemind.ai.memory.benchmark.locomo;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import java.util.List;
import java.util.Objects;

public final class LocomoMemoryWriter {

    private static final String BenchmarkAgentId = "memind-benchmark";

    public void write(Memory memory, LocomoDataset.LocomoUser user, boolean dualPerspective) {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(user, "user");

        var memoryId = DefaultMemoryId.of(user.id(), BenchmarkAgentId);
        for (LocomoDataset.LocomoSession session : user.sessions()) {
            memory.addMessages(memoryId, toPrimaryMessages(session, user)).block();
            if (dualPerspective) {
                memory.addMessages(memoryId, toMirroredMessages(session, user)).block();
            }
        }
    }

    private List<
                    com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message
                            .Message>
            toPrimaryMessages(LocomoDataset.LocomoSession session, LocomoDataset.LocomoUser user) {
        return session.messages().stream()
                .map(
                        message ->
                                toCoreMessage(
                                        message.speaker().equalsIgnoreCase(user.speakerA()),
                                        message.text(),
                                        message.timestamp(),
                                        message.speaker()))
                .toList();
    }

    private List<
                    com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message
                            .Message>
            toMirroredMessages(LocomoDataset.LocomoSession session, LocomoDataset.LocomoUser user) {
        return session.messages().stream()
                .map(
                        message ->
                                toCoreMessage(
                                        message.speaker().equalsIgnoreCase(user.speakerB()),
                                        message.text(),
                                        message.timestamp(),
                                        message.speaker()))
                .toList();
    }

    private com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message
            toCoreMessage(
                    boolean userRole, String text, java.time.Instant timestamp, String speaker) {
        if (userRole) {
            return com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message
                    .Message.user(text, timestamp, speaker);
        }
        return com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message
                .assistant(text, timestamp)
                .withUserName(speaker);
    }
}
