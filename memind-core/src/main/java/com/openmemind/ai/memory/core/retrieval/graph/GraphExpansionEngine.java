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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class GraphExpansionEngine {

    private final RetrievalGraphAssistant graphAssistant;

    public GraphExpansionEngine(RetrievalGraphAssistant graphAssistant) {
        this.graphAssistant = Objects.requireNonNull(graphAssistant, "graphAssistant");
    }

    public GraphExpansionResult expand(
            QueryContext context,
            RetrievalConfig config,
            RetrievalGraphSettings settings,
            List<ScoredResult> seeds) {
        boolean enabled = settings != null && settings.enabled();
        if (!enabled || seeds == null || seeds.isEmpty()) {
            return GraphExpansionResult.empty(enabled);
        }

        var seedIds =
                seeds.stream()
                        .map(ScoredResult::sourceId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var expanded =
                graphAssistant.assist(context, config, settings, seeds).block(settings.timeout());
        if (expanded == null) {
            return GraphExpansionResult.degraded(true, false);
        }
        var stats = expanded.stats();
        var graphItems =
                expanded.items().stream()
                        .filter(item -> !seedIds.contains(item.sourceId()))
                        .toList();
        return new GraphExpansionResult(
                graphItems,
                stats.graphEnabled(),
                stats.degraded(),
                stats.timedOut(),
                stats.seedCount(),
                stats.linkExpansionCount(),
                stats.entityExpansionCount(),
                stats.dedupedCandidateCount(),
                stats.overlapCount(),
                stats.skippedOverFanoutEntityCount());
    }
}
