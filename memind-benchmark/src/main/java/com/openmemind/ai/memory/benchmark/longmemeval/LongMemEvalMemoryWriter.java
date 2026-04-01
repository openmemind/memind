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
package com.openmemind.ai.memory.benchmark.longmemeval;

import com.openmemind.ai.memory.benchmark.core.dataset.Message;
import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import java.util.List;
import java.util.Objects;

public final class LongMemEvalMemoryWriter {

    private static final String BenchmarkAgentId = "memind-benchmark";

    public void writeQuestionHaystack(
            Memory memory, LongMemEvalDataset.LongMemEvalQuestion question) {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(question, "question");

        var memoryId = DefaultMemoryId.of(question.questionId(), BenchmarkAgentId);
        for (List<Message> session : question.haystackSessions()) {
            memory.addMessages(memoryId, toCoreMessages(session)).block();
        }
    }

    private List<
                    com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message
                            .Message>
            toCoreMessages(List<Message> session) {
        return session.stream().map(this::toCoreMessage).toList();
    }

    private com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message
            toCoreMessage(Message message) {
        if (isUserSpeaker(message.speaker())) {
            return com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message
                    .Message.user(message.text(), message.timestamp(), message.speaker());
        }
        return com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message
                .assistant(message.text(), message.timestamp())
                .withUserName(message.speaker());
    }

    private boolean isUserSpeaker(String speaker) {
        return speaker != null
                && ("user".equalsIgnoreCase(speaker) || "human".equalsIgnoreCase(speaker));
    }
}
