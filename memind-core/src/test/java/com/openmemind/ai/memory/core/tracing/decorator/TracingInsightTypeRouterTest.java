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

import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_INSIGHT_TYPE_ROUTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.retrieval.tier.InsightTypeRouter;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingInsightTypeRouterTest {

    @Test
    void routePublishesRoutingSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightTypeRouter.class);
        var routedTypes = List.of("preference", "behavior");
        when(delegate.route(any(), any(), any())).thenReturn(Mono.just(routedTypes));

        var traced = new TracingInsightTypeRouter(delegate, observer);

        StepVerifier.create(
                        traced.route(
                                "query",
                                List.of("history"),
                                Map.of("preference", "User preferences")))
                .expectNext(routedTypes)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName())
                .isEqualTo(RETRIEVAL_INSIGHT_TYPE_ROUTING);
        assertThat(observer.monoContexts().getFirst().requestAttributes()).isEmpty();
    }

    @Test
    void routePropagatesDelegateErrorsThroughObserver() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(InsightTypeRouter.class);
        when(delegate.route(any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        var traced = new TracingInsightTypeRouter(delegate, observer);

        StepVerifier.create(traced.route("query", List.of(), Map.of()))
                .expectErrorMessage("boom")
                .verify();

        assertThat(observer.monoContexts()).hasSize(1);
    }
}
