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
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_INSIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingInsightExtractStepTest {

    @Test
    void extractPublishesInsightSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightExtractStep.class);
        var result = InsightResult.empty();
        when(delegate.extract(any(), any())).thenReturn(Mono.just(result));

        var traced = new TracingInsightExtractStep(delegate, observer);
        var memoryId = TestMemoryIds.userAgent();

        StepVerifier.create(traced.extract(memoryId, MemoryItemResult.empty()))
                .expectNext(result)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName()).isEqualTo(EXTRACTION_INSIGHT);
        assertThat(observer.monoContexts().getFirst().requestAttributes())
                .containsEntry(MEMORY_ID, memoryId.toIdentifier());
    }

    @Test
    void extractPropagatesDelegateErrorsThroughObserver() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightExtractStep.class);
        when(delegate.extract(any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        var traced = new TracingInsightExtractStep(delegate, observer);

        StepVerifier.create(traced.extract(TestMemoryIds.userAgent(), MemoryItemResult.empty()))
                .expectErrorMessage("boom")
                .verify();

        assertThat(observer.monoContexts()).hasSize(1);
    }
}
