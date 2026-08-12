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

import com.openmemind.ai.memory.core.retrieval.scoring.observation.DefaultRetrievalResultMergerObservation;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import reactor.core.publisher.Mono;

/** Default adapter around {@link ResultMerger}. */
public final class DefaultRetrievalResultMerger implements RetrievalResultMerger {

    public static final DefaultRetrievalResultMerger INSTANCE = new DefaultRetrievalResultMerger();

    private final ObservationRegistry observationRegistry;

    public DefaultRetrievalResultMerger() {
        this(ObservationRegistry.NOOP);
    }

    public DefaultRetrievalResultMerger(ObservationRegistry observationRegistry) {
        this.observationRegistry =
                observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
    }

    @Override
    public Mono<List<ScoredResult>> merge(
            ScoringConfig scoring, List<List<ScoredResult>> rankedLists, double... weights) {
        return DefaultRetrievalResultMergerObservation.observe(
                observationRegistry,
                rankedLists,
                weights,
                () -> mergeInternal(scoring, rankedLists, weights));
    }

    private Mono<List<ScoredResult>> mergeInternal(
            ScoringConfig scoring, List<List<ScoredResult>> rankedLists, double... weights) {
        return Mono.fromSupplier(() -> ResultMerger.merge(scoring, rankedLists, weights));
    }
}
