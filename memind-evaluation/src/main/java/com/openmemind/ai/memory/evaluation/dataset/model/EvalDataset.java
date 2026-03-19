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
package com.openmemind.ai.memory.evaluation.dataset.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Evaluation dataset, containing a list of conversations and a list of QA pairs, providing convenient methods indexed by conversation ID
 *
 */
public record EvalDataset(String name, List<EvalConversation> conversations, List<QAPair> qaPairs) {
    public Map<String, EvalConversation> conversationIndex() {
        return conversations.stream()
                .collect(Collectors.toMap(EvalConversation::conversationId, c -> c));
    }

    public Map<String, List<QAPair>> qaPairsByConversation() {
        return qaPairs.stream().collect(Collectors.groupingBy(QAPair::conversationId));
    }
}
