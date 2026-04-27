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

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_CANDIDATE_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_DEDUPED_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_RESULT_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_SOURCE_LIST_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_WEIGHT_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_RESULT_MERGE;
import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.retrieval.scoring.RetrievalResultMerger;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingRetrievalResultMergerTest {

    @Test
    void mergePublishesResultMergeSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var merged = List.of(scoredResult("item-1"));
        RetrievalResultMerger delegate = (scoring, rankedLists, weights) -> Mono.just(merged);
        var rankedLists = List.of(List.of(scoredResult("item-1")), List.of(scoredResult("item-1")));

        var traced = new TracingRetrievalResultMerger(delegate, observer);

        StepVerifier.create(traced.merge(ScoringConfig.defaults(), rankedLists, 1.0d, 0.8d))
                .expectNext(merged)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        var observation = observer.monoContexts().getFirst();
        assertThat(observation.spanName()).isEqualTo(RETRIEVAL_RESULT_MERGE);
        assertThat(observation.requestAttributes())
                .containsEntry(RETRIEVAL_SOURCE_LIST_COUNT, 2)
                .containsEntry(RETRIEVAL_CANDIDATE_COUNT, 2)
                .containsEntry(RETRIEVAL_DEDUPED_COUNT, 1)
                .containsEntry(RETRIEVAL_WEIGHT_COUNT, 2);
        assertThat(resultAttributes(observation, merged))
                .containsEntry(RETRIEVAL_RESULT_COUNT, 1)
                .containsEntry(RETRIEVAL_DEDUPED_COUNT, 1);
    }

    private ScoredResult scoredResult(String id) {
        return new ScoredResult(ScoredResult.SourceType.ITEM, id, "text", 0.9f, 0.8d);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resultAttributes(
            ObservationContext<?> context, List<ScoredResult> result) {
        return ((ObservationContext<List<ScoredResult>>) context).resultExtractor().extract(result);
    }
}
