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

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_INSIGHT_TYPE;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_ITEM_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_INSIGHT_GROUP_CLASSIFY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingInsightGroupClassifierTest {

    @Test
    void classifyPublishesGroupClassifySpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightGroupClassifier.class);
        var item =
                new MemoryItem(
                        1L,
                        "user1:agent1",
                        "content",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        var result = Map.of("group-1", List.of(item));
        when(delegate.classify(any(), any(), any())).thenReturn(Mono.just(result));
        var insightType =
                new MemoryInsightType(
                        1L, "PROFILE", "desc", null, List.of(), 400, null, null, null, null, null,
                        null, null);

        var traced = new TracingInsightGroupClassifier(delegate, observer);

        StepVerifier.create(traced.classify(insightType, List.of(item), List.of("group-1")))
                .expectNext(result)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName())
                .isEqualTo(EXTRACTION_INSIGHT_GROUP_CLASSIFY);
        assertThat(observer.monoContexts().getFirst().requestAttributes())
                .containsEntry(EXTRACTION_INSIGHT_TYPE, "PROFILE")
                .containsEntry(EXTRACTION_ITEM_COUNT, 1);
    }

    @Test
    void classifyPropagatesDelegateErrorsThroughObserver() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightGroupClassifier.class);
        when(delegate.classify(any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));
        var insightType =
                new MemoryInsightType(
                        1L, "PROFILE", "desc", null, List.of(), 400, null, null, null, null, null,
                        null, null);

        var traced = new TracingInsightGroupClassifier(delegate, observer);

        StepVerifier.create(
                        traced.classify(
                                insightType,
                                List.of(
                                        new MemoryItem(
                                                1L,
                                                "user1:agent1",
                                                "content",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)),
                                List.of()))
                .expectErrorMessage("boom")
                .verify();

        assertThat(observer.monoContexts()).hasSize(1);
    }
}
