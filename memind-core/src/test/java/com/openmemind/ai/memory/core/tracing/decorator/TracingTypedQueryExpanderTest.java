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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.retrieval.deep.ExpandedQuery;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingTypedQueryExpanderTest {

    @Nested
    @DisplayName("expand()")
    class ExpandTests {

        @Test
        @DisplayName("Delegate to delegate and wrap through observer")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var expanded =
                    List.of(
                            new ExpandedQuery(ExpandedQuery.QueryType.VEC, "semantic query"),
                            new ExpandedQuery(ExpandedQuery.QueryType.LEX, "keyword query"));
            var delegate = mock(TypedQueryExpander.class);
            when(delegate.expand(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Mono.just(expanded));

            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<List<ExpandedQuery>>> op = invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingTypedQueryExpander(delegate, observer);
            var gaps = List.of("missing info");
            var keyInfo = List.of("known info");

            StepVerifier.create(traced.expand("test query", gaps, keyInfo, List.of(), 3))
                    .expectNext(expanded)
                    .verifyComplete();

            verify(observer)
                    .observeMono(
                            argThat(ctx -> ctx.spanName().equals(RETRIEVAL_MULTI_QUERY_EXPAND)),
                            any());
            verify(delegate).expand("test query", gaps, keyInfo, List.of(), 3);
        }
    }
}
