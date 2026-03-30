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
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingMemoryRetrieverTest {

    @Test
    void retrievePublishesRetrievalSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(MemoryRetriever.class);
        var result = RetrievalResult.empty("simple", "query");
        when(delegate.retrieve(any())).thenReturn(Mono.just(result));

        var traced = new TracingMemoryRetriever(delegate, observer);
        var memoryId = TestMemoryIds.userAgent();
        var request = RetrievalRequest.of(memoryId, "query", RetrievalConfig.Strategy.SIMPLE);

        StepVerifier.create(traced.retrieve(request)).expectNext(result).verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName()).isEqualTo(RETRIEVAL);
        assertThat(observer.monoContexts().getFirst().requestAttributes())
                .containsEntry(MEMORY_ID, memoryId.toIdentifier());
    }

    @Test
    void retrievePropagatesDelegateErrorsThroughObserver() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(MemoryRetriever.class);
        when(delegate.retrieve(any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        var traced = new TracingMemoryRetriever(delegate, observer);

        StepVerifier.create(
                        traced.retrieve(
                                RetrievalRequest.of(
                                        TestMemoryIds.userAgent(),
                                        "query",
                                        RetrievalConfig.Strategy.SIMPLE)))
                .expectErrorMessage("boom")
                .verify();

        assertThat(observer.monoContexts()).hasSize(1);
    }
}
