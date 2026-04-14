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
package com.openmemind.ai.memory.plugin.rawdata.document.caption;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class LlmDocumentCaptionGeneratorTest {

    @Test
    void generateNormalizesStructuredResponse() {
        var client =
                new StubClient(
                        new LlmDocumentCaptionGenerator.CaptionResponse(
                                "Retry policy", "Backoff for 429 and 503 responses."));
        var generator = new LlmDocumentCaptionGenerator(client, true, 4, 240);

        assertThat(generator.generate("retry content", Map.of(), "English").block())
                .isEqualTo("Retry policy\n\nBackoff for 429 and 503 responses.");
    }

    @Test
    void generateFallsBackWhenClientErrors() {
        var generator =
                new LlmDocumentCaptionGenerator(
                        new FailingClient(new IllegalStateException("boom")), true, 4, 240);

        assertThat(
                        generator
                                .generate(
                                        "Use exponential backoff for 429 and 503 retries.",
                                        Map.of("headingTitle", "Retry policy"),
                                        "English")
                                .block())
                .isEqualTo("Retry policy: Use exponential backoff for 429 and 503 retries.");
    }

    @Test
    void generateFallsBackWhenClientReturnsBlankCaption() {
        var generator =
                new LlmDocumentCaptionGenerator(
                        new StubClient(new LlmDocumentCaptionGenerator.CaptionResponse("  ", "  ")),
                        true,
                        4,
                        240);

        assertThat(
                        generator
                                .generate(
                                        "Use exponential backoff for 429 and 503 retries.",
                                        Map.of("headingTitle", "Retry policy"),
                                        "English")
                                .block())
                .isEqualTo("Retry policy: Use exponential backoff for 429 and 503 retries.");
    }

    @Test
    void generateUsesFallbackWhenCaptionDisabled() {
        var generator =
                new LlmDocumentCaptionGenerator(
                        new FailingClient(
                                new AssertionError("caption client should not be called")),
                        false,
                        4,
                        240);

        assertThat(
                        generator
                                .generate(
                                        "Use exponential backoff for 429 and 503 retries.",
                                        Map.of("headingTitle", "Retry policy"),
                                        "English")
                                .block())
                .isEqualTo("Retry policy: Use exponential backoff for 429 and 503 retries.");
    }

    @Test
    void generateForwardsLanguageToPromptMessages() {
        var client =
                new CapturingClient(
                        new LlmDocumentCaptionGenerator.CaptionResponse(
                                "Retry policy", "Backoff for 429 and 503 responses."));
        var generator = new LlmDocumentCaptionGenerator(client, true, 4, 240);

        generator.generate("retry content", Map.of(), "Chinese").block();

        assertThat(client.lastMessages()).isNotEmpty();
        assertThat(client.lastMessages().getFirst().content())
                .contains("Output Language = Chinese");
    }

    @Test
    void generateForSegmentsPreservesInputOrderWhenResponsesCompleteOutOfOrder() {
        var client =
                new DelayedClient(
                        Map.of(
                                "chunk-1", Duration.ofMillis(80),
                                "chunk-2", Duration.ofMillis(10),
                                "chunk-3", Duration.ofMillis(40)));
        var generator = new LlmDocumentCaptionGenerator(client, true, 2, 240);
        var segments =
                List.of(
                        new Segment("chunk-1", null, new CharBoundary(0, 7), Map.of()),
                        new Segment("chunk-2", null, new CharBoundary(8, 15), Map.of()),
                        new Segment("chunk-3", null, new CharBoundary(16, 23), Map.of()));

        assertThat(generator.generateForSegments(segments, "English").block())
                .extracting(Segment::caption)
                .containsExactly("chunk-1 title", "chunk-2 title", "chunk-3 title");
    }

    @Test
    void generateForSegmentsHonorsConfiguredConcurrency() {
        var client = new TrackingClient(Duration.ofMillis(40));
        var generator = new LlmDocumentCaptionGenerator(client, true, 2, 240);
        var segments =
                IntStream.range(0, 6)
                        .mapToObj(
                                i ->
                                        new Segment(
                                                "chunk-" + i,
                                                null,
                                                new CharBoundary(i, i + 1),
                                                Map.of()))
                        .toList();

        generator.generateForSegments(segments, "English").block();

        assertThat(client.maxInFlight()).isLessThanOrEqualTo(2);
    }

    private static final class StubClient implements StructuredChatClient {

        private final Object response;

        private StubClient(Object response) {
            this.response = response;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("not used"));
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            return Mono.just(responseType.cast(response));
        }
    }

    private static final class FailingClient implements StructuredChatClient {

        private final Throwable error;

        private FailingClient(Throwable error) {
            this.error = error;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("not used"));
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            return Mono.error(error);
        }
    }

    private static final class CapturingClient implements StructuredChatClient {

        private final Object response;
        private final AtomicReference<List<ChatMessage>> lastMessages = new AtomicReference<>();

        private CapturingClient(Object response) {
            this.response = response;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("not used"));
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            lastMessages.set(List.copyOf(messages));
            return Mono.just(responseType.cast(response));
        }

        private List<ChatMessage> lastMessages() {
            return lastMessages.get();
        }
    }

    private static final class DelayedClient implements StructuredChatClient {

        private final Map<String, Duration> delays;

        private DelayedClient(Map<String, Duration> delays) {
            this.delays = delays;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("not used"));
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            String content = messages.getLast().content();
            String chunk =
                    content.substring(
                                    content.indexOf("<CONTENT>") + 9, content.indexOf("</CONTENT>"))
                            .trim();
            Duration delay = delays.getOrDefault(chunk, Duration.ZERO);
            Object response = new LlmDocumentCaptionGenerator.CaptionResponse(chunk + " title", "");
            return Mono.delay(delay).map(ignored -> responseType.cast(response));
        }
    }

    private static final class TrackingClient implements StructuredChatClient {

        private final Duration delay;
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxInFlight = new AtomicInteger();

        private TrackingClient(Duration delay) {
            this.delay = delay;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("not used"));
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            int current = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(current, Math::max);
            Object response =
                    new LlmDocumentCaptionGenerator.CaptionResponse(
                            messages.getLast().content(), "summary");
            return Mono.delay(delay)
                    .map(ignored -> responseType.cast(response))
                    .doOnNext(ignored -> inFlight.decrementAndGet())
                    .doOnError(ignored -> inFlight.decrementAndGet());
        }

        private int maxInFlight() {
            return maxInFlight.get();
        }
    }
}
