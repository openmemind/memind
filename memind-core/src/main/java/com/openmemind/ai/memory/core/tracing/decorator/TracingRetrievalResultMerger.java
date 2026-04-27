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

import com.openmemind.ai.memory.core.retrieval.scoring.RetrievalResultMerger;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Tracing decorator for strategy-level retrieval result fusion. */
public final class TracingRetrievalResultMerger extends TracingSupport
        implements RetrievalResultMerger {

    private final RetrievalResultMerger delegate;

    public TracingRetrievalResultMerger(RetrievalResultMerger delegate, MemoryObserver observer) {
        super(Objects.requireNonNull(observer, "observer"));
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Mono<List<ScoredResult>> merge(
            ScoringConfig scoring, List<List<ScoredResult>> rankedLists, double... weights) {
        return trace(
                MemorySpanNames.RETRIEVAL_RESULT_MERGE,
                requestAttributes(rankedLists, weights),
                result -> resultAttributes(rankedLists, result),
                () -> delegate.merge(scoring, rankedLists, weights));
    }

    private Map<String, Object> requestAttributes(
            List<List<ScoredResult>> rankedLists, double[] weights) {
        return Map.of(
                MemoryAttributes.RETRIEVAL_SOURCE_LIST_COUNT,
                rankedLists == null ? 0 : rankedLists.size(),
                MemoryAttributes.RETRIEVAL_CANDIDATE_COUNT,
                candidateCount(rankedLists),
                MemoryAttributes.RETRIEVAL_DEDUPED_COUNT,
                dedupedCount(rankedLists),
                MemoryAttributes.RETRIEVAL_WEIGHT_COUNT,
                weights == null ? 0 : weights.length);
    }

    private Map<String, Object> resultAttributes(
            List<List<ScoredResult>> rankedLists, List<ScoredResult> result) {
        int before = candidateCount(rankedLists);
        int after = result == null ? 0 : result.size();
        return Map.of(
                MemoryAttributes.RETRIEVAL_RESULT_COUNT,
                after,
                MemoryAttributes.RETRIEVAL_DEDUPED_COUNT,
                Math.max(0, before - after));
    }

    private int candidateCount(List<List<ScoredResult>> rankedLists) {
        if (rankedLists == null) {
            return 0;
        }
        return rankedLists.stream().filter(Objects::nonNull).mapToInt(List::size).sum();
    }

    private int dedupedCount(List<List<ScoredResult>> rankedLists) {
        if (rankedLists == null) {
            return 0;
        }
        return (int)
                rankedLists.stream()
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .map(ScoredResult::dedupKey)
                        .distinct()
                        .count();
    }
}
