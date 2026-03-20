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
package com.openmemind.ai.memory.core.extraction.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("DefaultBoundaryDetector Unit Test")
class DefaultBoundaryDetectorTest {

    private static final BoundaryDetectionContext EMPTY_CONTEXT = BoundaryDetectionContext.empty();

    /**
     * Test subclass that short-circuits the LLM call so we can control L3 results without needing
     * a real ChatClient.
     */
    private static class LlmStubDetector extends DefaultBoundaryDetector {

        private final Mono<BoundaryDecision> stubbedResult;

        LlmStubDetector(BoundaryDetectorConfig config, Mono<BoundaryDecision> stubbedResult) {
            super(config, mock(ChatClient.class));
            this.stubbedResult = stubbedResult;
        }

        @Override
        protected Mono<BoundaryDecision> callLlm(
                List<Message> buffer, BoundaryDetectionContext context) {
            return stubbedResult;
        }
    }

    @Nested
    @DisplayName("L1 Hard Limit")
    class HardLimitTests {

        @Test
        @DisplayName("When buffer reaches maxMessages, should seal with reason=hard_limit")
        void shouldSealWhenBufferReachesMaxMessages() {
            var config = new BoundaryDetectorConfig(5, 8192, 3);
            var detector = new DefaultBoundaryDetector(config);

            List<Message> buffer =
                    IntStream.range(0, 5).mapToObj(i -> Message.user("Message" + i)).toList();

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isTrue();
                                assertThat(decision.confidence()).isEqualTo(1.0);
                                assertThat(decision.reason()).isEqualTo("hard_limit");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("When buffer exceeds maxMessages, should also seal")
        void shouldSealWhenBufferExceedsMaxMessages() {
            var config = new BoundaryDetectorConfig(3, 8192, 2);
            var detector = new DefaultBoundaryDetector(config);

            List<Message> buffer =
                    IntStream.range(0, 10).mapToObj(i -> Message.user("Message" + i)).toList();

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isTrue();
                                assertThat(decision.reason()).isEqualTo("hard_limit");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("When buffer is below maxMessages, L1 should not trigger")
        void shouldNotTriggerL1WhenBufferBelowMax() {
            var config = new BoundaryDetectorConfig(10, 8192, 8);
            var detector = new DefaultBoundaryDetector(config);

            List<Message> buffer =
                    List.of(Message.user("Hello"), Message.assistant("Hello, how can I help you?"));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(decision -> assertThat(decision.shouldSeal()).isFalse())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("L1.5 Token Limit")
    class TokenLimitTests {

        @Test
        @DisplayName("When maxTokens is set very low, token limit triggers")
        void shouldSealWhenMaxTokensIsVeryLow() {
            // maxTokens=1 guarantees any non-empty message exceeds the limit
            var config = new BoundaryDetectorConfig(100, 1, 6);
            var detector = new DefaultBoundaryDetector(config);

            List<Message> buffer = List.of(Message.user("Hello world"));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isTrue();
                                assertThat(decision.confidence()).isEqualTo(1.0);
                                assertThat(decision.reason()).isEqualTo("token_limit");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("When maxTokens is set very high, token limit does not trigger")
        void shouldNotSealWhenMaxTokensIsVeryHigh() {
            var config = new BoundaryDetectorConfig(100, 999_999, 6);
            var detector = new DefaultBoundaryDetector(config);

            List<Message> buffer = List.of(Message.user("Short message"));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(decision -> assertThat(decision.shouldSeal()).isFalse())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("L3 LLM Detection")
    class LlmDetectionTests {

        @Test
        @DisplayName("When buffer reaches minMessagesForLlm and LLM returns seal, should seal")
        void shouldSealWhenLlmDetectsTopicChange() {
            var config = new BoundaryDetectorConfig(20, 8192, 3);
            var detector =
                    new LlmStubDetector(
                            config, Mono.just(BoundaryDecision.seal(0.85, "topic_change")));

            List<Message> buffer =
                    List.of(
                            Message.user("Let's talk about Java"),
                            Message.assistant("Java is an object-oriented language"),
                            Message.user("Let's talk about Python"));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isTrue();
                                assertThat(decision.confidence()).isEqualTo(0.85);
                                assertThat(decision.reason()).isEqualTo("topic_change");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("When buffer is below minMessagesForLlm, skip LLM detection")
        void shouldSkipLlmWhenBufferBelowMinMessages() {
            var config = new BoundaryDetectorConfig(20, 8192, 5);
            var detector =
                    new LlmStubDetector(config, Mono.just(BoundaryDecision.seal(1.0, "llm")));

            List<Message> buffer = List.of(Message.user("Hello"), Message.assistant("Hello!"));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(decision -> assertThat(decision.shouldSeal()).isFalse())
                    .verifyComplete();
        }

        @Test
        @DisplayName("When chatClient is null, skip L3 detection")
        void shouldSkipL3WhenChatClientIsNull() {
            var config = new BoundaryDetectorConfig(20, 8192, 2);
            var detector = new DefaultBoundaryDetector(config);

            List<Message> buffer =
                    List.of(
                            Message.user("Hello"),
                            Message.assistant("Hello!"),
                            Message.user("Goodbye"));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(decision -> assertThat(decision.shouldSeal()).isFalse())
                    .verifyComplete();
        }

        @Test
        @DisplayName("When LLM detection fails, fallback to hold")
        void shouldFallbackToHoldWhenLlmFails() {
            var config = new BoundaryDetectorConfig(20, 8192, 2);
            var detector =
                    new LlmStubDetector(
                            config, Mono.error(new RuntimeException("LLM service unavailable")));

            List<Message> buffer =
                    List.of(
                            Message.user("Hello"),
                            Message.assistant("Hello!"),
                            Message.user("Goodbye"));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isFalse();
                                assertThat(decision.reason()).isEqualTo("hold");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("When LLM returns hold, do not seal")
        void shouldHoldWhenLlmReturnsHold() {
            var config = new BoundaryDetectorConfig(20, 8192, 2);
            var detector = new LlmStubDetector(config, Mono.just(BoundaryDecision.hold()));

            List<Message> buffer =
                    List.of(
                            Message.user("Hello"),
                            Message.assistant("Hello!"),
                            Message.user("What is your name?"));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(decision -> assertThat(decision.shouldSeal()).isFalse())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Context Passing")
    class ContextPassingTests {

        @Test
        @DisplayName("timeGap should be correctly computed and passed to LLM")
        void shouldPassTimeGapInContext() {
            var config = new BoundaryDetectorConfig(20, 8192, 2);
            var capturedContext = new BoundaryDetectionContext[1];
            var detector =
                    new LlmStubDetector(config, Mono.just(BoundaryDecision.hold())) {
                        @Override
                        protected Mono<BoundaryDecision> callLlm(
                                List<Message> buffer, BoundaryDetectionContext ctx) {
                            capturedContext[0] = ctx;
                            return Mono.just(BoundaryDecision.hold());
                        }
                    };

            Instant now = Instant.now();
            List<Message> buffer =
                    List.of(
                            Message.user("Hello", now.minus(Duration.ofMinutes(10))),
                            Message.assistant("Hello!", now.minus(Duration.ofMinutes(5))),
                            Message.user("Goodbye", now));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .expectNextCount(1)
                    .verifyComplete();

            assertThat(capturedContext[0]).isNotNull();
            assertThat(capturedContext[0].lastTimeGap()).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("When messages have no timestamps, timeGap should be null")
        void shouldPassNullTimeGapWhenNoTimestamps() {
            var config = new BoundaryDetectorConfig(20, 8192, 2);
            var capturedContext = new BoundaryDetectionContext[1];
            var detector =
                    new LlmStubDetector(config, Mono.just(BoundaryDecision.hold())) {
                        @Override
                        protected Mono<BoundaryDecision> callLlm(
                                List<Message> buffer, BoundaryDetectionContext ctx) {
                            capturedContext[0] = ctx;
                            return Mono.just(BoundaryDecision.hold());
                        }
                    };

            List<Message> buffer =
                    List.of(
                            Message.user("Hello"),
                            Message.assistant("Hello!"),
                            Message.user("Goodbye"));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .expectNextCount(1)
                    .verifyComplete();

            assertThat(capturedContext[0].lastTimeGap()).isNull();
        }
    }

    @Nested
    @DisplayName("No LLM Time Gap Fallback")
    class NoLlmFallbackTests {

        @Test
        @DisplayName("Without LLM, time gap exceeding 30 minutes should seal")
        void shouldSealWhenTimeGapExceedsThresholdWithoutLlm() {
            var config = new BoundaryDetectorConfig(20, 8192, 6);
            var detector = new DefaultBoundaryDetector(config);

            Instant now = Instant.now();
            List<Message> buffer =
                    List.of(
                            Message.user("Good morning", now.minus(Duration.ofHours(2))),
                            Message.assistant("Good morning!", now.minus(Duration.ofHours(1))),
                            Message.user("Good evening", now));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isTrue();
                                assertThat(decision.confidence()).isEqualTo(0.9);
                                assertThat(decision.reason()).isEqualTo("time_gap");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName(
                "With LLM configured, time-gap fallback should still trigger below LLM threshold")
        void shouldTriggerTimeGapFallbackBelowLlmThreshold() {
            var config = new BoundaryDetectorConfig(20, 8192, 6);
            var detector = new LlmStubDetector(config, Mono.just(BoundaryDecision.hold()));

            Instant now = Instant.now();
            List<Message> buffer =
                    List.of(
                            Message.user("Good morning", now.minus(Duration.ofHours(2))),
                            Message.user("Good evening", now));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isTrue();
                                assertThat(decision.reason()).isEqualTo("time_gap");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName(
                "When LLM threshold is reached, LLM detection should take precedence over time gap")
        void shouldPreferLlmDetectionWhenThresholdReached() {
            var config = new BoundaryDetectorConfig(20, 8192, 2);
            var llmCalled = new boolean[1];
            var detector =
                    new LlmStubDetector(config, Mono.just(BoundaryDecision.hold())) {
                        @Override
                        protected Mono<BoundaryDecision> callLlm(
                                List<Message> buffer, BoundaryDetectionContext context) {
                            llmCalled[0] = true;
                            return Mono.just(BoundaryDecision.hold());
                        }
                    };

            Instant now = Instant.now();
            List<Message> buffer =
                    List.of(
                            Message.user("Good morning", now.minus(Duration.ofHours(2))),
                            Message.user("Good evening", now));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(decision -> assertThat(decision.shouldSeal()).isFalse())
                    .verifyComplete();

            assertThat(llmCalled[0]).isTrue();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null buffer should return hold")
        void shouldHoldForNullBuffer() {
            var detector = new DefaultBoundaryDetector(new BoundaryDetectorConfig(10, 8192, 6));

            StepVerifier.create(detector.shouldSeal(null, EMPTY_CONTEXT))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isFalse();
                                assertThat(decision.reason()).isEqualTo("hold");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Empty buffer should return hold")
        void shouldHoldForEmptyBuffer() {
            var detector = new DefaultBoundaryDetector(new BoundaryDetectorConfig(10, 8192, 6));

            StepVerifier.create(detector.shouldSeal(List.of(), EMPTY_CONTEXT))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isFalse();
                                assertThat(decision.reason()).isEqualTo("hold");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("L1 takes priority: hard message limit fires before token limit")
        void l1TakesPriorityOverTokenLimit() {
            // maxMessages=3, maxTokens=1 — both would trigger, L1 must win
            var config = new BoundaryDetectorConfig(3, 1, 6);
            var detector = new DefaultBoundaryDetector(config);

            List<Message> buffer =
                    List.of(Message.user("m1"), Message.assistant("m2"), Message.user("m3"));

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(
                            decision -> {
                                assertThat(decision.shouldSeal()).isTrue();
                                assertThat(decision.reason()).isEqualTo("hard_limit");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("When some messages lack timestamps, time-gap fallback should not trigger")
        void shouldNotTriggerWhenPartialTimestampsMissing() {
            var detector = new DefaultBoundaryDetector(new BoundaryDetectorConfig(20, 8192, 6));

            List<Message> buffer =
                    List.of(
                            Message.user("Hello", Instant.now().minus(Duration.ofHours(2))),
                            Message.assistant("Hello!")); // no timestamp

            StepVerifier.create(detector.shouldSeal(buffer, EMPTY_CONTEXT))
                    .assertNext(decision -> assertThat(decision.shouldSeal()).isFalse())
                    .verifyComplete();
        }
    }
}
