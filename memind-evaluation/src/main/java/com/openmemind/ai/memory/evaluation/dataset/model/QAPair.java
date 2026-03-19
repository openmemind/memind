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
 * Evaluation question and answer pair, including question, standard answer, category, evidence, and multiple choice options metadata
 *
 */
public record QAPair(
        String questionId,
        String conversationId,
        String question,
        String goldenAnswer,
        String category,
        List<String> evidence,
        Map<String, Object> metadata) {
    public boolean isMultipleChoice() {
        return metadata.containsKey("all_options");
    }

    public String allOptions() {
        return (String) metadata.getOrDefault("all_options", "");
    }
}
