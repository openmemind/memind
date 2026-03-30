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
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_ITEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingMemoryItemExtractStepTest {

    @Test
    void extractPublishesItemSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(MemoryItemExtractStep.class);
        var result = MemoryItemResult.empty();
        when(delegate.extract(any(), any(), any())).thenReturn(Mono.just(result));

        var traced = new TracingMemoryItemExtractStep(delegate, observer);
        var memoryId = TestMemoryIds.userAgent();

        StepVerifier.create(
                        traced.extract(
                                memoryId, RawDataResult.empty(), ItemExtractionConfig.defaults()))
                .expectNext(result)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName()).isEqualTo(EXTRACTION_ITEM);
        assertThat(observer.monoContexts().getFirst().requestAttributes())
                .containsEntry(MEMORY_ID, memoryId.toIdentifier());
    }

    @Test
    void extractPropagatesDelegateErrorsThroughObserver() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(MemoryItemExtractStep.class);
        when(delegate.extract(any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        var traced = new TracingMemoryItemExtractStep(delegate, observer);

        StepVerifier.create(
                        traced.extract(
                                TestMemoryIds.userAgent(),
                                RawDataResult.empty(),
                                ItemExtractionConfig.defaults()))
                .expectErrorMessage("boom")
                .verify();

        assertThat(observer.monoContexts()).hasSize(1);
    }
}
