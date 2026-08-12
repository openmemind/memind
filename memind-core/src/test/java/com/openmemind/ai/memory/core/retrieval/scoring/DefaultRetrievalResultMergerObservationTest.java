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
package com.openmemind.ai.memory.core.retrieval.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.retrieval.scoring.observation.DefaultRetrievalResultMergerObservation;
import com.openmemind.ai.memory.core.retrieval.scoring.observation.DefaultRetrievalResultMergerObservation.ResultMergeObservationContext;
import com.openmemind.ai.memory.core.support.RecordingObservationRegistry;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.Observation;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DefaultRetrievalResultMergerObservationTest {

    @Test
    void recordsRemovedDuplicateCountAfterMerge() {
        var registry = new RecordingObservationRegistry();
        var first = scored("1", 0.9);
        var second = scored("2", 0.8);
        var duplicate = scored("1", 0.7);
        var merger = new DefaultRetrievalResultMerger(registry);

        var result =
                merger.merge(
                                ScoringConfig.defaults(),
                                List.of(List.of(first, second), List.of(duplicate)),
                                1.0,
                                1.0)
                        .block();

        assertThat(result).hasSize(2);
        assertThat(registry.observations())
                .singleElement()
                .satisfies(
                        observation ->
                                assertThat(observation.resultAttributes())
                                        .containsEntry("memind.retrieval.candidate_count", "3")
                                        .containsEntry("memind.retrieval.result_count", "2")
                                        .containsEntry("memind.retrieval.deduped_count", "1"));
    }

    @Test
    void usesMatchingGlobalConventionInsteadOfDefaultConvention() {
        var registry = new RecordingObservationRegistry();
        registry.observationConfig().observationConvention(new ResultMergeGlobalConvention());

        DefaultRetrievalResultMergerObservation.observe(
                        registry,
                        List.of(List.of(scored("1", 0.9))),
                        new double[] {1.0},
                        () -> Mono.just(List.of(scored("1", 0.9))))
                .block();

        assertThat(registry.observations())
                .singleElement()
                .satisfies(
                        observation ->
                                assertThat(observation.resultAttributes())
                                        .containsEntry("test.global_convention", "applied"));
    }

    private static ScoredResult scored(String id, double score) {
        return new ScoredResult(ScoredResult.SourceType.ITEM, id, "item-" + id, 0.8f, score);
    }

    private static final class ResultMergeGlobalConvention
            implements GlobalObservationConvention<ResultMergeObservationContext> {

        @Override
        public String getName() {
            return "test.result_merge";
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(ResultMergeObservationContext context) {
            return KeyValues.of("test.global_convention", "applied");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ResultMergeObservationContext;
        }
    }
}
