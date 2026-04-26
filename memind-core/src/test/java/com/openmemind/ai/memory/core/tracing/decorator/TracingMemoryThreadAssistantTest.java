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
package com.openmemind.ai.memory.core.tracing.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistResult;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistant;
import com.openmemind.ai.memory.core.retrieval.thread.RetrievalMemoryThreadSettings;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingMemoryThreadAssistantTest {

    @Test
    void tracingDecoratorShouldEmitFullAssistObservationSurface() {
        var observer = new RecordingMemoryObserver();
        MemoryThreadAssistant delegate =
                (context, config, settings, directWindow) ->
                        Mono.just(
                                new MemoryThreadAssistResult(
                                        directWindow,
                                        MemoryThreadAssistResult.Stats.success(1, 2, 2, false)));
        var traced = new TracingMemoryThreadAssistant(delegate, observer);
        var direct =
                List.of(
                        new ScoredResult(
                                ScoredResult.SourceType.ITEM, "101", "item-101", 0.8f, 1.0d));

        StepVerifier.create(
                        traced.assist(
                                new QueryContext(
                                        TestMemoryIds.userAgent(),
                                        "what changed",
                                        null,
                                        List.of(),
                                        Map.of(),
                                        null,
                                        null),
                                RetrievalConfig.simple(),
                                new TestThreadSettings(true, 1, 2, 1, Duration.ofMillis(150)),
                                direct))
                .assertNext(result -> assertThat(result.items()).containsExactlyElementsOf(direct))
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        var observation = observer.monoContexts().getFirst();
        assertThat(observation.spanName())
                .isEqualTo(MemorySpanNames.RETRIEVAL_MEMORY_THREAD_ASSIST);
        assertThat(observation.requestAttributes())
                .containsEntry(MemoryAttributes.MEMORY_ID, TestMemoryIds.userAgent().toIdentifier())
                .containsEntry(MemoryAttributes.RETRIEVAL_MEMORY_THREAD_ENABLED, true);

        @SuppressWarnings("unchecked")
        var resultAttributes =
                ((com.openmemind.ai.memory.core.tracing.ObservationContext<
                                        MemoryThreadAssistResult>)
                                observation)
                        .resultExtractor()
                        .extract(
                                new MemoryThreadAssistResult(
                                        direct,
                                        MemoryThreadAssistResult.Stats.success(1, 2, 2, false)));
        assertThat(resultAttributes)
                .containsEntry(MemoryAttributes.RETRIEVAL_MEMORY_THREAD_SEED_THREAD_COUNT, 1)
                .containsEntry(MemoryAttributes.RETRIEVAL_MEMORY_THREAD_CANDIDATE_COUNT, 2)
                .containsEntry(MemoryAttributes.RETRIEVAL_MEMORY_THREAD_ADMITTED_COUNT, 2)
                .containsEntry(MemoryAttributes.RETRIEVAL_MEMORY_THREAD_CLAMPED, false)
                .containsEntry(MemoryAttributes.RETRIEVAL_MEMORY_THREAD_DEGRADED, false)
                .containsEntry(MemoryAttributes.RETRIEVAL_MEMORY_THREAD_TIMEOUT, false);
    }

    private record TestThreadSettings(
            boolean enabled,
            int maxThreads,
            int maxMembersPerThread,
            int protectDirectTopK,
            Duration timeout)
            implements RetrievalMemoryThreadSettings {}
}
