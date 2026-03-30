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

import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_SUFFICIENCY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyResult;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingSufficiencyGateTest {

    @Test
    void checkPublishesSufficiencySpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(SufficiencyGate.class);
        var result = SufficiencyResult.fallbackInsufficient();
        when(delegate.check(any(), any())).thenReturn(Mono.just(result));

        var traced = new TracingSufficiencyGate(delegate, observer);
        var context =
                new QueryContext(
                        TestMemoryIds.userAgent(), "query", null, List.of(), Map.of(), null, null);
        var scored =
                List.of(new ScoredResult(ScoredResult.SourceType.ITEM, "id-1", "text", 0.9f, 0.85));

        StepVerifier.create(traced.check(context, scored)).expectNext(result).verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName()).isEqualTo(RETRIEVAL_SUFFICIENCY);
        assertThat(observer.monoContexts().getFirst().requestAttributes()).isEmpty();
    }

    @Test
    void checkPropagatesDelegateErrorsThroughObserver() {
        var observer = new RecordingMemoryObserver();
        var delegate = mock(SufficiencyGate.class);
        when(delegate.check(any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        var traced = new TracingSufficiencyGate(delegate, observer);

        StepVerifier.create(
                        traced.check(
                                new QueryContext(
                                        TestMemoryIds.userAgent(),
                                        "query",
                                        null,
                                        List.of(),
                                        Map.of(),
                                        null,
                                        null),
                                List.of()))
                .expectErrorMessage("boom")
                .verify();

        assertThat(observer.monoContexts()).hasSize(1);
    }
}
