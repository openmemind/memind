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

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_QUERY;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_TOP_K;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_RERANK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingRerankerTest {

    @Test
    void rerankPublishesRerankSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(Reranker.class);
        var results =
                List.of(new ScoredResult(ScoredResult.SourceType.ITEM, "id-1", "text", 0.9f, 0.85));
        when(delegate.rerank(any(), any(), any(int.class))).thenReturn(Mono.just(results));

        var traced = new TracingReranker(delegate, observer);

        StepVerifier.create(traced.rerank("query", results, 5))
                .expectNext(results)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName()).isEqualTo(RETRIEVAL_RERANK);
        assertThat(observer.monoContexts().getFirst().requestAttributes())
                .containsEntry(RETRIEVAL_QUERY, "query")
                .containsEntry(RETRIEVAL_TOP_K, 5);
    }

    @Test
    void rerankPropagatesDelegateErrorsThroughObserver() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(Reranker.class);
        when(delegate.rerank(any(), any(), any(int.class)))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        var traced = new TracingReranker(delegate, observer);

        StepVerifier.create(traced.rerank("query", List.of(), 5))
                .expectErrorMessage("boom")
                .verify();

        assertThat(observer.monoContexts()).hasSize(1);
    }
}
