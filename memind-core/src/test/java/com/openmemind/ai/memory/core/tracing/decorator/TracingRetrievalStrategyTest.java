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

import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_STRATEGY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingRetrievalStrategyTest {

    @Nested
    @DisplayName("retrieve()")
    class RetrieveTests {

        @Test
        @DisplayName("Delegate to delegate and wrap with observer")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var result = RetrievalResult.empty("simple", "test-query");
            var delegate = mock(RetrievalStrategy.class);
            when(delegate.name()).thenReturn("simple");
            when(delegate.retrieve(any(), any())).thenReturn(Mono.just(result));

            // NoopMemoryObserver is final, so use mock(MemoryObserver.class) with thenAnswer
            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<RetrievalResult>> op = invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingRetrievalStrategy(delegate, observer);
            var memoryId = mock(MemoryId.class);
            when(memoryId.toIdentifier()).thenReturn("test-id");
            var queryContext =
                    new QueryContext(
                            memoryId, "test query", "test query", List.of(), Map.of(), null, null);
            var config = RetrievalConfig.simple();

            StepVerifier.create(traced.retrieve(queryContext, config))
                    .expectNext(result)
                    .verifyComplete();

            verify(observer)
                    .observeMono(argThat(ctx -> ctx.spanName().equals(RETRIEVAL_STRATEGY)), any());
            verify(delegate).retrieve(any(), any());
        }
    }

    @Nested
    @DisplayName("name()")
    class NameTests {

        @Test
        @DisplayName("Delegate to delegate")
        void delegatesName() {
            var delegate = mock(RetrievalStrategy.class);
            when(delegate.name()).thenReturn("deep");
            var traced = new TracingRetrievalStrategy(delegate, mock(MemoryObserver.class));
            assertThat(traced.name()).isEqualTo("deep");
        }
    }

    @Nested
    @DisplayName("onDataChanged()")
    class OnDataChangedTests {

        @Test
        @DisplayName("Delegate to delegate")
        void delegatesOnDataChanged() {
            var delegate = mock(RetrievalStrategy.class);
            var traced = new TracingRetrievalStrategy(delegate, mock(MemoryObserver.class));
            var memoryId = mock(MemoryId.class);
            traced.onDataChanged(memoryId);
            verify(delegate).onDataChanged(memoryId);
        }
    }
}
