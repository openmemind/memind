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

import com.openmemind.ai.memory.benchmark.core.dataset.Dataset;
import com.openmemind.ai.memory.benchmark.core.dataset.Message;
import java.util.List;

public record LocomoDataset(String name, List<LocomoUser> users) implements Dataset {

    @Override
    public int conversationCount() {
        return users.size();
    }

    @Override
    public int questionCount() {
        return users.stream().mapToInt(user -> user.questions().size()).sum();
    }

    public record LocomoUser(
            String id,
            String speakerA,
            String speakerB,
            List<LocomoSession> sessions,
            List<LocomoQuestion> questions) {}

    public record LocomoSession(int index, String dateTime, List<Message> messages) {}

    public record LocomoQuestion(String id, String question, String answer, int category) {}
}
