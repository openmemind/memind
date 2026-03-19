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

import com.openmemind.ai.memory.core.retrieval.tier.InsightTypeRouter;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link InsightTypeRouter}.
 */
public class TracingInsightTypeRouter extends TracingSupport implements InsightTypeRouter {

    private final InsightTypeRouter delegate;

    public TracingInsightTypeRouter(InsightTypeRouter delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<List<String>> route(
            String query, List<String> conversationHistory, Map<String, String> availableTypes) {
        return trace(
                MemorySpanNames.RETRIEVAL_INSIGHT_TYPE_ROUTING,
                Map.of(),
                () -> delegate.route(query, conversationHistory, availableTypes));
    }
}
