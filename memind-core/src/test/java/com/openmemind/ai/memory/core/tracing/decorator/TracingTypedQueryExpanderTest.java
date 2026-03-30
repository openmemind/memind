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

import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_MULTI_QUERY_EXPAND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.retrieval.deep.ExpandedQuery;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingTypedQueryExpanderTest {

    @Test
    void expandPublishesExpansionSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(TypedQueryExpander.class);
        var expanded =
                List.of(
                        new ExpandedQuery(ExpandedQuery.QueryType.VEC, "semantic query"),
                        new ExpandedQuery(ExpandedQuery.QueryType.LEX, "keyword query"));
        when(delegate.expand(any(), any(), any(), any(), anyInt())).thenReturn(Mono.just(expanded));

        var traced = new TracingTypedQueryExpander(delegate, observer);

        StepVerifier.create(
                        traced.expand(
                                "query", List.of("gap"), List.of("info"), List.of("history"), 3))
                .expectNext(expanded)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName())
                .isEqualTo(RETRIEVAL_MULTI_QUERY_EXPAND);
        assertThat(observer.monoContexts().getFirst().requestAttributes()).isEmpty();
    }

    @Test
    void expandPropagatesDelegateErrorsThroughObserver() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(TypedQueryExpander.class);
        when(delegate.expand(any(), any(), any(), any(), anyInt()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        var traced = new TracingTypedQueryExpander(delegate, observer);

        StepVerifier.create(traced.expand("query", List.of(), List.of(), List.of(), 3))
                .expectErrorMessage("boom")
                .verify();

        assertThat(observer.monoContexts()).hasSize(1);
    }
}
