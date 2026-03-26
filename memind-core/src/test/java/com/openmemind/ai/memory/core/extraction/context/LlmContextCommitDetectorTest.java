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
package com.openmemind.ai.memory.core.extraction.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("LlmContextCommitDetector Unit Test")
class LlmContextCommitDetectorTest {

    private static final CommitDetectionContext EMPTY_CONTEXT = CommitDetectionContext.empty();

    private static class StubLlmContextCommitDetector extends LlmContextCommitDetector {

        private final Mono<CommitDecision> stubbedResult;

        StubLlmContextCommitDetector(
                CommitDetectorConfig config, Mono<CommitDecision> stubbedResult) {
            super(config, new NoopStructuredChatClient());
            this.stubbedResult = stubbedResult;
        }

        @Override
        protected Mono<CommitDecision> callLlm(
                CommitDetectionInput input, CommitDetectionContext context) {
            return stubbedResult;
        }
    }

    @Nested
    @DisplayName("Hard limit")
    class HardLimitTests {

        @Test
        @DisplayName("Should seal when buffer reaches maxMessages")
        void shouldSealWhenBufferReachesMaxMessages() {
            var detector = new LlmContextCommitDetector(new CommitDetectorConfig(3, 8192, 2));
            var input =
                    new CommitDetectionInput(
                            List.of(Message.user("m0"), Message.assistant("m1")),
                            List.of(Message.user("m2")),
                            EMPTY_CONTEXT);

            StepVerifier.create(detector.shouldCommit(input))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isTrue();
                                assertThat(decision.reason()).isEqualTo("hard_limit");
                            })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("LLM detection")
    class LlmDetectionTests {

        @Test
        @DisplayName("Should seal when LLM detection returns a sealing decision")
        void shouldSealWhenLlmDetectionReturnsASealingDecision() {
            var detector =
                    new StubLlmContextCommitDetector(
                            new CommitDetectorConfig(20, 8192, 3),
                            Mono.just(CommitDecision.commit(0.85, "topic_change")));
            var input =
                    new CommitDetectionInput(
                            List.of(
                                    Message.user("Let's talk about Java"),
                                    Message.assistant("Java is object oriented")),
                            List.of(Message.user("Let's talk about Python")),
                            EMPTY_CONTEXT);

            StepVerifier.create(detector.shouldCommit(input))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isTrue();
                                assertThat(decision.confidence()).isEqualTo(0.85);
                                assertThat(decision.reason()).isEqualTo("topic_change");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should hold when the structured LLM call fails")
        void shouldHoldWhenStructuredLlmCallFails() {
            var detector =
                    new StubLlmContextCommitDetector(
                            new CommitDetectorConfig(20, 8192, 2),
                            Mono.error(new RuntimeException("LLM unavailable")));
            var input =
                    new CommitDetectionInput(
                            List.of(Message.user("Hello"), Message.assistant("Hello!")),
                            List.of(Message.user("Goodbye")),
                            EMPTY_CONTEXT);

            StepVerifier.create(detector.shouldCommit(input))
                    .assertNext(decision -> assertThat(decision.shouldSeal()).isFalse())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Fallback")
    class FallbackTests {

        @Test
        @DisplayName("Should seal on long time gap when no LLM client is configured")
        void shouldSealOnLongTimeGapWhenNoLlmClientConfigured() {
            var detector = new LlmContextCommitDetector(new CommitDetectorConfig(20, 8192, 3));
            var input =
                    new CommitDetectionInput(
                            List.of(Message.user("Earlier", Instant.parse("2024-03-15T10:00:00Z"))),
                            List.of(
                                    Message.assistant(
                                            "Later", Instant.parse("2024-03-15T10:45:00Z"))),
                            EMPTY_CONTEXT);

            StepVerifier.create(detector.shouldCommit(input))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isTrue();
                                assertThat(decision.reason()).isEqualTo("time_gap");
                            })
                    .verifyComplete();
        }
    }

    private static final class NoopStructuredChatClient implements StructuredChatClient {

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.empty();
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            return Mono.empty();
        }
    }
}
