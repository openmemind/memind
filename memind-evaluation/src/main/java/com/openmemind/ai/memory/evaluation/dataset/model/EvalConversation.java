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

/**
 * Evaluation conversation model, including conversation ID, message list, and speaker metadata
 *
 */
public record EvalConversation(
        String conversationId, List<EvalMessage> messages, Map<String, Object> metadata) {
    public String speakerA() {
        return (String) metadata.getOrDefault("speaker_a", "");
    }

    public String speakerB() {
        return (String) metadata.getOrDefault("speaker_b", "");
    }
}
