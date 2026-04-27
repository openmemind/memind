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
package com.openmemind.ai.memory.server.domain.memory.response;

import com.openmemind.ai.memory.core.retrieval.trace.RetrievalCandidateTrace;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalFinalTrace;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalMergeTrace;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RetrievalTraceView(
        String traceId,
        Instant startedAt,
        Instant completedAt,
        boolean truncated,
        List<StageView> stages,
        MergeView merge,
        FinalView finalResults) {

    public record StageView(
            String stage,
            String tier,
            String method,
            String status,
            Integer inputCount,
            Integer candidateCount,
            Integer resultCount,
            boolean degraded,
            boolean skipped,
            Instant startedAt,
            Long durationMillis,
            Map<String, Object> attributes,
            List<RetrievalCandidateTrace> candidates) {}

    public record MergeView(
            int inputCount,
            int outputCount,
            int deduplicatedCount,
            int sourceCount,
            String status) {
        public static MergeView from(RetrievalMergeTrace trace) {
            return trace == null
                    ? null
                    : new MergeView(
                            trace.inputCount(),
                            trace.outputCount(),
                            trace.deduplicatedCount(),
                            trace.sourceCount(),
                            trace.status());
        }
    }

    public record FinalView(
            String strategy,
            String status,
            int itemCount,
            int insightCount,
            int rawDataCount,
            int evidenceCount) {
        public static FinalView from(RetrievalFinalTrace trace) {
            return trace == null
                    ? null
                    : new FinalView(
                            trace.strategy(),
                            trace.status(),
                            trace.itemCount(),
                            trace.insightCount(),
                            trace.rawDataCount(),
                            trace.evidenceCount());
        }
    }
}
