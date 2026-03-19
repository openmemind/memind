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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.dedup.DeduplicationResult;
import com.openmemind.ai.memory.core.extraction.item.dedup.MemoryItemDeduplicator;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingMemoryItemDeduplicatorTest {

    @Nested
    @DisplayName("deduplicate()")
    class DeduplicateTests {

        @Test
        @DisplayName("Delegate to delegate and wrap with observer")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var result = new DeduplicationResult(List.of(), List.of());
            var memoryId = mock(MemoryId.class);
            when(memoryId.toIdentifier()).thenReturn("test-id");
            List<ExtractedMemoryEntry> entries = List.of();

            var delegate = mock(MemoryItemDeduplicator.class);
            when(delegate.spanName()).thenReturn("memind.extraction.item.dedup");
            when(delegate.deduplicate(any(), any())).thenReturn(Mono.just(result));

            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<DeduplicationResult>> op = invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingMemoryItemDeduplicator(delegate, observer);

            StepVerifier.create(traced.deduplicate(memoryId, entries))
                    .expectNext(result)
                    .verifyComplete();

            verify(observer)
                    .observeMono(
                            argThat(ctx -> ctx.spanName().equals("memind.extraction.item.dedup")),
                            any());
            verify(delegate).deduplicate(memoryId, entries);
        }
    }

    @Nested
    @DisplayName("spanName()")
    class SpanNameTests {

        @Test
        @DisplayName("Delegate to delegate")
        void delegatesSpanName() {
            var delegate = mock(MemoryItemDeduplicator.class);
            when(delegate.spanName()).thenReturn("memind.extraction.item.dedup");
            var traced = new TracingMemoryItemDeduplicator(delegate, mock(MemoryObserver.class));
            assertThat(traced.spanName()).isEqualTo("memind.extraction.item.dedup");
        }
    }
}
