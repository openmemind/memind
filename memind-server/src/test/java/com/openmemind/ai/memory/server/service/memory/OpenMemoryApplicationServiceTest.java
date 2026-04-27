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
package com.openmemind.ai.memory.server.service.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.ExtractionStatus;
import com.openmemind.ai.memory.core.extraction.context.ContextRequest;
import com.openmemind.ai.memory.core.extraction.context.ContextWindow;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalFinalTrace;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceContext;
import com.openmemind.ai.memory.server.configuration.MemindServerObservabilityProperties;
import com.openmemind.ai.memory.server.domain.memory.request.AddMessageRequest;
import com.openmemind.ai.memory.server.domain.memory.request.CommitMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.ExtractMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.RetrieveMemoryRequest;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeUnavailableException;
import com.openmemind.ai.memory.server.runtime.RuntimeHandle;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class OpenMemoryApplicationServiceTest {

    @Test
    void extractDispatchesAsynchronouslyAndReleasesLeaseAfterCompletion() throws Exception {
        RecordingMemory memory = new RecordingMemory();
        memory.extractSink = Sinks.one();
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1));
        OpenMemoryApplicationService service = new OpenMemoryApplicationService(runtimeManager);

        service.extractAsync(
                new ExtractMemoryRequest(
                        "u1",
                        "a1",
                        new com.openmemind.ai.memory.core.extraction.rawdata.content
                                .ConversationContent(List.of())));

        assertThat(memory.extractInvoked.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(memory.lastMemoryId).isEqualTo(DefaultMemoryId.of("u1", "a1"));
        assertThat(runtimeManager.currentHandle().inFlightRequests()).hasValue(1);

        memory.extractSink.tryEmitValue(extractionResult());

        awaitInFlightRequests(runtimeManager, 0);
    }

    @Test
    void addMessageDispatchesAsynchronouslyAndReleasesLeaseAfterCompletion() throws Exception {
        RecordingMemory memory = new RecordingMemory();
        memory.addMessageSink = Sinks.empty();
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1));
        OpenMemoryApplicationService service = new OpenMemoryApplicationService(runtimeManager);

        service.addMessageAsync(
                new AddMessageRequest("u1", "a1", Message.user("hello", Instant.now())));

        assertThat(memory.addMessageInvoked.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(memory.lastMemoryId).isEqualTo(DefaultMemoryId.of("u1", "a1"));
        assertThat(runtimeManager.currentHandle().inFlightRequests()).hasValue(1);

        memory.addMessageSink.tryEmitEmpty();

        awaitInFlightRequests(runtimeManager, 0);
    }

    @Test
    void commitDispatchesAsynchronouslyAndReleasesLeaseAfterCompletion() throws Exception {
        RecordingMemory memory = new RecordingMemory();
        memory.commitSink = Sinks.one();
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1));
        OpenMemoryApplicationService service = new OpenMemoryApplicationService(runtimeManager);

        service.commitAsync(new CommitMemoryRequest("u1", "a1"));

        assertThat(memory.commitInvoked.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(memory.lastMemoryId).isEqualTo(DefaultMemoryId.of("u1", "a1"));
        assertThat(runtimeManager.currentHandle().inFlightRequests()).hasValue(1);

        memory.commitSink.tryEmitValue(extractionResult());

        awaitInFlightRequests(runtimeManager, 0);
    }

    @Test
    void extractAsyncFailsFastWhenRuntimeIsUnavailable() {
        OpenMemoryApplicationService service =
                new OpenMemoryApplicationService(new MemoryRuntimeManager());

        assertThatThrownBy(
                        () ->
                                service.extractAsync(
                                        new ExtractMemoryRequest(
                                                "u1",
                                                "a1",
                                                new com.openmemind.ai.memory.core.extraction.rawdata
                                                        .content.ConversationContent(List.of()))))
                .isInstanceOf(MemoryRuntimeUnavailableException.class);
    }

    @Test
    void retrieveMapsRetrievalResult() {
        RecordingMemory memory = new RecordingMemory();
        memory.retrieveResult =
                new RetrievalResult(
                        List.of(
                                new ScoredResult(
                                        ScoredResult.SourceType.ITEM,
                                        "item-1",
                                        "loves coffee",
                                        0.82F,
                                        0.91,
                                        Instant.parse("2026-03-30T10:00:00Z"))),
                        List.of(
                                new RetrievalResult.InsightResult(
                                        "insight-1", "prefers concise answers", InsightTier.LEAF)),
                        List.of(
                                new RetrievalResult.RawDataResult(
                                        "rd-1", "user mentioned coffee", 0.76, List.of("item-1"))),
                        List.of("user likes coffee"),
                        "SIMPLE",
                        "coffee");
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1));
        OpenMemoryApplicationService service = new OpenMemoryApplicationService(runtimeManager);

        var response =
                service.retrieve(
                        new RetrieveMemoryRequest(
                                "u1", "a1", "coffee", RetrievalConfig.Strategy.SIMPLE));

        assertThat(response.items()).singleElement().extracting("id").isEqualTo("item-1");
        assertThat(response.insights()).singleElement().extracting("tier").isEqualTo("LEAF");
        assertThat(response.rawData()).singleElement().extracting("rawDataId").isEqualTo("rd-1");
        assertThat(runtimeManager.currentHandle().inFlightRequests()).hasValue(0);
    }

    @Test
    void retrieveIncludesDebugTraceWhenEnabledAndRequested() {
        RecordingMemory memory = new RecordingMemory();
        memory.retrieveResult = RetrievalResult.empty("SIMPLE", "coffee");
        memory.recordTrace = true;
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1));
        MemindServerObservabilityProperties properties = new MemindServerObservabilityProperties();
        properties.getRetrievalTrace().setEnabled(true);
        OpenMemoryApplicationService service =
                new OpenMemoryApplicationService(runtimeManager, properties);

        var response =
                service.retrieve(
                        new RetrieveMemoryRequest(
                                "u1", "a1", "coffee", RetrievalConfig.Strategy.SIMPLE, true));

        assertThat(response.trace()).isNotNull();
        assertThat(response.trace().finalResults().strategy()).isEqualTo("SIMPLE");
        assertThat(response.trace().traceId()).isNotBlank();
    }

    private static ExtractionResult extractionResult() {
        MemoryId memoryId = DefaultMemoryId.of("u1", "a1");
        return new ExtractionResult(
                memoryId,
                new RawDataResult(
                        List.of(
                                new MemoryRawData(
                                        "rd-1",
                                        memoryId.toIdentifier(),
                                        "CONVERSATION",
                                        "content-1",
                                        null,
                                        "user mentioned coffee",
                                        "vec-1",
                                        Map.of(),
                                        null,
                                        null,
                                        Instant.parse("2026-03-31T10:00:00Z"),
                                        Instant.parse("2026-03-31T10:00:00Z"),
                                        Instant.parse("2026-03-31T10:01:00Z"))),
                        List.of(),
                        false),
                new MemoryItemResult(
                        List.of(
                                new MemoryItem(
                                        101L,
                                        memoryId.toIdentifier(),
                                        "loves coffee",
                                        MemoryScope.USER,
                                        MemoryCategory.PROFILE,
                                        "CONVERSATION",
                                        "vec-2",
                                        "rd-1",
                                        "hash-1",
                                        Instant.parse("2026-03-31T10:00:00Z"),
                                        Instant.parse("2026-03-31T10:00:00Z"),
                                        Map.of(),
                                        Instant.parse("2026-03-31T10:02:00Z"),
                                        MemoryItemType.FACT)),
                        List.of()),
                new InsightResult(
                        List.of(
                                new MemoryInsight(
                                        201L,
                                        memoryId.toIdentifier(),
                                        "preference",
                                        MemoryScope.USER,
                                        "coffee preference",
                                        List.of("preference"),
                                        List.of(),
                                        null,
                                        Instant.parse("2026-03-31T10:03:00Z"),
                                        List.of(),
                                        Instant.parse("2026-03-31T10:03:00Z"),
                                        Instant.parse("2026-03-31T10:03:00Z"),
                                        InsightTier.LEAF,
                                        null,
                                        List.of(),
                                        1))),
                ExtractionStatus.SUCCESS,
                Duration.ofMillis(120),
                null,
                false);
    }

    private static void awaitInFlightRequests(
            MemoryRuntimeManager runtimeManager, int expectedValue) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            if (runtimeManager.currentHandle().inFlightRequests().get() == expectedValue) {
                return;
            }
            Thread.sleep(10);
        }
        assertThat(runtimeManager.currentHandle().inFlightRequests()).hasValue(expectedValue);
    }

    private static final class RecordingMemory implements Memory {

        private MemoryId lastMemoryId;
        private Sinks.One<ExtractionResult> extractSink;
        private Sinks.Empty<ExtractionResult> addMessageSink;
        private Sinks.One<ExtractionResult> commitSink;
        private final CountDownLatch extractInvoked = new CountDownLatch(1);
        private final CountDownLatch addMessageInvoked = new CountDownLatch(1);
        private final CountDownLatch commitInvoked = new CountDownLatch(1);
        private RetrievalResult retrieveResult;
        private boolean recordTrace;

        @Override
        public Mono<ExtractionResult> extract(MemoryId memoryId, RawContent content) {
            this.lastMemoryId = memoryId;
            this.extractInvoked.countDown();
            return extractSink.asMono();
        }

        @Override
        public Mono<ExtractionResult> extract(
                MemoryId memoryId, RawContent content, ExtractionConfig config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<ExtractionResult> addMessages(MemoryId memoryId, List<Message> messages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<ExtractionResult> addMessages(
                MemoryId memoryId, List<Message> messages, ExtractionConfig config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<ExtractionResult> addMessage(MemoryId memoryId, Message message) {
            this.lastMemoryId = memoryId;
            this.addMessageInvoked.countDown();
            return addMessageSink.asMono();
        }

        @Override
        public Mono<ExtractionResult> addMessage(
                MemoryId memoryId, Message message, ExtractionConfig config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<ContextWindow> getContext(ContextRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<ExtractionResult> commit(MemoryId memoryId) {
            this.lastMemoryId = memoryId;
            this.commitInvoked.countDown();
            return commitSink.asMono();
        }

        @Override
        public Mono<ExtractionResult> commit(MemoryId memoryId, ExtractionConfig config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<RetrievalResult> retrieve(
                MemoryId memoryId, String query, RetrievalConfig.Strategy strategy) {
            this.lastMemoryId = memoryId;
            return Mono.deferContextual(
                    context -> {
                        if (recordTrace) {
                            RetrievalTraceContext.collector(context)
                                    .finalResults(
                                            new RetrievalFinalTrace("SIMPLE", "empty", 0, 0, 0, 0));
                        }
                        return Mono.just(retrieveResult);
                    });
        }

        @Override
        public Mono<RetrievalResult> retrieve(RetrievalRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Void> deleteItems(MemoryId memoryId, Collection<Long> itemIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Void> deleteInsights(MemoryId memoryId, Collection<Long> insightIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {}
    }
}
