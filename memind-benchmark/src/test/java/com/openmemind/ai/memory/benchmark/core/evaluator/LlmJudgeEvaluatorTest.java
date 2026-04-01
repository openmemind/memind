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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.benchmark.core.prompt.PromptTemplate;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class LlmJudgeEvaluatorTest {

    @Test
    void evaluateParsesStructuredJudgeResponse() {
        StructuredChatClient chatClient =
                new StructuredChatClient() {
                    @Override
                    public Mono<String> call(List<ChatMessage> messages) {
                        return Mono.just(
                                "{\"verdict\":\"CORRECT\",\"score\":1.0,\"reason\":\"matched\"}");
                    }

                    @Override
                    public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
                        throw new UnsupportedOperationException();
                    }
                };

        LlmJudgeEvaluator evaluator =
                new LlmJudgeEvaluator(
                        chatClient,
                        PromptTemplate.inline("Question: {{question}}\nAnswer: {{generated}}"));

        Judgment judgment =
                evaluator
                        .evaluate("Where did we meet?", "In Hangzhou.", "In Hangzhou.", Map.of())
                        .block();

        assertThat(judgment.verdict()).isEqualTo("CORRECT");
        assertThat(judgment.score()).isEqualTo(1.0);
        assertThat(judgment.reason()).isEqualTo("matched");
    }
}
