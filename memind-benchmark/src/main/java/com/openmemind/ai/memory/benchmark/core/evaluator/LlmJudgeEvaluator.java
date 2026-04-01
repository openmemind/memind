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
package com.openmemind.ai.memory.benchmark.core.evaluator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.benchmark.core.prompt.PromptTemplate;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class LlmJudgeEvaluator implements Evaluator {

    private final StructuredChatClient chatClient;
    private final PromptTemplate promptTemplate;
    private final ObjectMapper objectMapper;

    public LlmJudgeEvaluator(StructuredChatClient chatClient, PromptTemplate promptTemplate) {
        this(chatClient, promptTemplate, new ObjectMapper());
    }

    LlmJudgeEvaluator(
            StructuredChatClient chatClient,
            PromptTemplate promptTemplate,
            ObjectMapper objectMapper) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        this.promptTemplate = Objects.requireNonNull(promptTemplate, "promptTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Mono<Judgment> evaluate(
            String question, String generated, String golden, Map<String, Object> context) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (context != null) {
            variables.putAll(context);
        }
        variables.put("question", question);
        variables.put("generated", generated);
        variables.put("golden", golden);
        String renderedPrompt = promptTemplate.render(variables);
        return chatClient
                .call(ChatMessages.systemUser("", renderedPrompt))
                .map(this::parseJudgment);
    }

    private Judgment parseJudgment(String rawResponse) {
        try {
            return objectMapper.readValue(rawResponse, Judgment.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to parse judge response: " + rawResponse, exception);
        }
    }
}
