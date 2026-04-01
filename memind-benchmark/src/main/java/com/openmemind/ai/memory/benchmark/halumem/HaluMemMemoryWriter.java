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
package com.openmemind.ai.memory.benchmark.halumem;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import java.util.Objects;

public final class HaluMemMemoryWriter {

    private static final String BenchmarkAgentId = "memind-benchmark";

    public void writeSession(Memory memory, String userId, HaluMemDataset.HaluMemSession session) {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(session, "session");

        var memoryId = DefaultMemoryId.of(userId, BenchmarkAgentId);
        memory.addMessages(
                        memoryId,
                        session.dialogue().stream()
                                .filter(message -> "user".equalsIgnoreCase(message.speaker()))
                                .map(
                                        message ->
                                                com.openmemind.ai.memory.core.extraction.rawdata
                                                        .content.conversation.message.Message.user(
                                                        message.text(),
                                                        message.timestamp(),
                                                        message.speaker()))
                                .toList())
                .block();
    }
}
