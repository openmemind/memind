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
package com.openmemind.ai.memory.core.retrieval.graph;

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.graph.GraphQueryBudgetContext;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class DefaultGraphItemChannel implements GraphItemChannel {

    private static final Logger log = LoggerFactory.getLogger(DefaultGraphItemChannel.class);

    private final GraphExpansionEngine engine;

    public DefaultGraphItemChannel(GraphExpansionEngine engine) {
        this.engine = engine;
    }

    @Override
    public Mono<GraphExpansionResult> retrieve(
            QueryContext context,
            RetrievalConfig config,
            RetrievalGraphSettings settings,
            List<ScoredResult> seeds) {
        boolean enabled = settings != null && settings.enabled();
        if (!enabled || seeds == null || seeds.isEmpty() || engine == null) {
            return Mono.just(GraphExpansionResult.empty(enabled));
        }
        Duration effectiveTimeout = shorterPositive(settings.timeout(), config.timeout());
        return Mono.fromCallable(
                        () -> {
                            try (var ignored = GraphQueryBudgetContext.open(effectiveTimeout)) {
                                return engine.expand(context, config, settings, seeds);
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(effectiveTimeout)
                .onErrorResume(
                        TimeoutException.class,
                        error -> Mono.just(GraphExpansionResult.degraded(true, true)))
                .onErrorResume(
                        error -> {
                            log.warn("Simple: Graph item channel failed", error);
                            return Mono.just(GraphExpansionResult.degraded(true, false));
                        });
    }

    private Duration shorterPositive(Duration first, Duration second) {
        if (first == null || first.isZero() || first.isNegative()) {
            return second;
        }
        if (second == null || second.isZero() || second.isNegative()) {
            return first;
        }
        return first.compareTo(second) <= 0 ? first : second;
    }
}
