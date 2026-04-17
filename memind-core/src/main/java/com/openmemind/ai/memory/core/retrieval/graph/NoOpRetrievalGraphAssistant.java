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
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Disabled-mode retrieval graph assistant.
 */
public final class NoOpRetrievalGraphAssistant implements RetrievalGraphAssistant {

    public static final NoOpRetrievalGraphAssistant INSTANCE =
            new NoOpRetrievalGraphAssistant();

    private NoOpRetrievalGraphAssistant() {}

    @Override
    public Mono<RetrievalGraphAssistResult> assist(
            QueryContext context,
            RetrievalConfig config,
            SimpleStrategyConfig strategyConfig,
            List<ScoredResult> directItems) {
        return Mono.just(RetrievalGraphAssistResult.directOnly(directItems, false));
    }
}
