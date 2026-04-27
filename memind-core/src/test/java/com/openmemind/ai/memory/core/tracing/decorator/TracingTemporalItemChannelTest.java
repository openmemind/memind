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
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_CANDIDATE_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_CHANNEL;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_RESULT_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_TEMPORAL_CONSTRAINT_PRESENT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_TEMPORAL_DEGRADED;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_TEMPORAL_ENABLED;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_TEMPORAL_CHANNEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalConstraint;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannel;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannelResult;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannelSettings;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingTemporalItemChannelTest {

    @Test
    void retrievePublishesTemporalChannelSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        TemporalItemChannel delegate = mock(TemporalItemChannel.class);
        var context = queryContext();
        var config = RetrievalConfig.simple();
        Optional<TemporalConstraint> constraint = Optional.empty();
        var settings = TemporalItemChannelSettings.defaults();
        var item = scoredResult();
        var result = new TemporalItemChannelResult(List.of(item), true, false, false, 7);
        when(delegate.retrieve(context, config, constraint, settings))
                .thenReturn(Mono.just(result));

        var traced = new TracingTemporalItemChannel(delegate, observer);

        StepVerifier.create(traced.retrieve(context, config, constraint, settings))
                .expectNext(result)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        var observation = observer.monoContexts().getFirst();
        assertThat(observation.spanName()).isEqualTo(RETRIEVAL_TEMPORAL_CHANNEL);
        assertThat(observation.requestAttributes())
                .containsEntry(MEMORY_ID, "memory")
                .containsEntry(RETRIEVAL_CHANNEL, "temporal")
                .containsEntry(RETRIEVAL_TEMPORAL_ENABLED, true)
                .containsEntry(RETRIEVAL_TEMPORAL_CONSTRAINT_PRESENT, false);
        assertThat(resultAttributes(observation, result))
                .containsEntry(RETRIEVAL_RESULT_COUNT, 1)
                .containsEntry(RETRIEVAL_CANDIDATE_COUNT, 7)
                .containsEntry(RETRIEVAL_TEMPORAL_DEGRADED, false);
    }

    private QueryContext queryContext() {
        return new QueryContext(
                DefaultMemoryId.of("memory", null), "query", null, List.of(), Map.of(), null, null);
    }

    private ScoredResult scoredResult() {
        return new ScoredResult(ScoredResult.SourceType.ITEM, "item-1", "text", 0.9f, 0.8d);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resultAttributes(
            ObservationContext<?> context, TemporalItemChannelResult result) {
        return ((ObservationContext<TemporalItemChannelResult>) context)
                .resultExtractor()
                .extract(result);
    }
}
