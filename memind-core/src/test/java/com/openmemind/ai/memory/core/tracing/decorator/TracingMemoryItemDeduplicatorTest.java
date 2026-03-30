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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.extraction.item.dedup.DeduplicationResult;
import com.openmemind.ai.memory.core.extraction.item.dedup.MemoryItemDeduplicator;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingMemoryItemDeduplicatorTest {

    @Test
    void deduplicatePublishesDelegateSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(MemoryItemDeduplicator.class);
        var result = new DeduplicationResult(java.util.List.of(), java.util.List.of());
        when(delegate.spanName()).thenReturn("memind.extraction.item.dedup");
        when(delegate.deduplicate(any(), any())).thenReturn(Mono.just(result));

        var traced = new TracingMemoryItemDeduplicator(delegate, observer);
        var memoryId = TestMemoryIds.userAgent();

        StepVerifier.create(traced.deduplicate(memoryId, java.util.List.of()))
                .expectNext(result)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName())
                .isEqualTo("memind.extraction.item.dedup");
        assertThat(observer.monoContexts().getFirst().requestAttributes())
                .containsEntry(MEMORY_ID, memoryId.toIdentifier());
    }

    @Test
    void deduplicatePropagatesDelegateErrorsThroughObserver() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(MemoryItemDeduplicator.class);
        when(delegate.spanName()).thenReturn("memind.extraction.item.dedup");
        when(delegate.deduplicate(any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        var traced = new TracingMemoryItemDeduplicator(delegate, observer);

        StepVerifier.create(traced.deduplicate(TestMemoryIds.userAgent(), java.util.List.of()))
                .expectErrorMessage("boom")
                .verify();

        assertThat(observer.monoContexts()).hasSize(1);
    }
}
