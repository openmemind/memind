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

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.MEMORY_ID;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_STRATEGY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingRetrievalStrategyTest {

    @Test
    void retrievePublishesRetrievalStrategySpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(RetrievalStrategy.class);
        var result = RetrievalResult.empty("simple", "query");
        when(delegate.name()).thenReturn("simple");
        when(delegate.retrieve(any(), any())).thenReturn(Mono.just(result));

        var traced = new TracingRetrievalStrategy(delegate, observer);
        var memoryId = TestMemoryIds.userAgent();
        var context =
                new QueryContext(memoryId, "query", null, List.of("history"), Map.of(), null, null);

        StepVerifier.create(traced.retrieve(context, RetrievalConfig.simple()))
                .expectNext(result)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName()).isEqualTo(RETRIEVAL_STRATEGY);
        assertThat(observer.monoContexts().getFirst().requestAttributes())
                .containsEntry(MEMORY_ID, memoryId.toIdentifier())
                .containsEntry(MemoryAttributes.RETRIEVAL_STRATEGY, "simple");
    }

    @Test
    void retrievePropagatesDelegateErrorsThroughObserver() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(RetrievalStrategy.class);
        when(delegate.name()).thenReturn("simple");
        when(delegate.retrieve(any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        var traced = new TracingRetrievalStrategy(delegate, observer);

        StepVerifier.create(
                        traced.retrieve(
                                new QueryContext(
                                        TestMemoryIds.userAgent(),
                                        "query",
                                        null,
                                        List.of(),
                                        Map.of(),
                                        null,
                                        null),
                                RetrievalConfig.simple()))
                .expectErrorMessage("boom")
                .verify();

        assertThat(observer.monoContexts()).hasSize(1);
    }
}
