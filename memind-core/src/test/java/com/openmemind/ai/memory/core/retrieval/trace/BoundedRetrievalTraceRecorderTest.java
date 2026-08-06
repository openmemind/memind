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
package com.openmemind.ai.memory.core.retrieval.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoundedRetrievalTraceRecorderTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-05T00:00:01Z");

    @Test
    void recordStageEventAppendsBoundedStageTrace() {
        var collector =
                new BoundedRetrievalTraceRecorder(
                        "trace-1", STARTED_AT, new RetrievalTraceOptions(8, 4, 16));

        collector.record(
                event(
                        new RetrievalTraceEvent.StagePayload(
                                "tier",
                                "item",
                                "vector",
                                3,
                                2,
                                1,
                                false,
                                false,
                                Map.of("source", "vector"),
                                List.of(
                                        new RetrievalTraceEvent.CandidatePayload(
                                                "item", 1, 0.9d, 0.8f, "candidate")))));

        var trace = collector.snapshot().orElseThrow();
        assertThat(trace.traceId()).isEqualTo("trace-1");
        assertThat(trace.stages()).hasSize(1);
        assertThat(trace.stages().getFirst().stage()).isEqualTo("tier");
        assertThat(trace.stages().getFirst().candidateCount()).isEqualTo(2);
        assertThat(trace.stages().getFirst().candidates())
                .extracting(RetrievalCandidateTrace::textPreview)
                .containsExactly("candidate");
    }

    @Test
    void recordMergeAndFinalEventsPopulateSnapshotSections() {
        var collector =
                new BoundedRetrievalTraceRecorder(
                        "trace-1", STARTED_AT, RetrievalTraceOptions.defaults());

        collector.record(event(new RetrievalTraceEvent.MergePayload(6, 4, 2, 3)));
        collector.record(event(new RetrievalTraceEvent.FinalPayload("simple", 2, 1, 0, 3)));

        var trace = collector.snapshot().orElseThrow();
        assertThat(trace.merge()).isEqualTo(new RetrievalMergeTrace(6, 4, 2, 3, "success"));
        assertThat(trace.finalResults())
                .isEqualTo(new RetrievalFinalTrace("simple", "success", 2, 1, 0, 3));
    }

    @Test
    void recordStageEventMarksTraceTruncatedWhenStageLimitExceeded() {
        var collector =
                new BoundedRetrievalTraceRecorder(
                        "trace-1", STARTED_AT, new RetrievalTraceOptions(1, 4, 16));

        collector.record(stageEvent("first"));
        collector.record(stageEvent("second"));

        var trace = collector.snapshot().orElseThrow();
        assertThat(trace.stages()).hasSize(1);
        assertThat(trace.stages().getFirst().method()).isEqualTo("first");
        assertThat(trace.truncated()).isTrue();
    }

    private static RetrievalTraceEvent stageEvent(String method) {
        return event(
                new RetrievalTraceEvent.StagePayload(
                        "tier", "item", method, null, null, 0, false, false, Map.of(), List.of()));
    }

    private static RetrievalTraceEvent event(RetrievalTraceEvent.Payload payload) {
        return new RetrievalTraceEvent(
                "memind.test",
                "memind.test",
                "success",
                STARTED_AT,
                COMPLETED_AT,
                1000L,
                Map.of("operation", "retrieval"),
                Map.of(),
                payload);
    }
}
