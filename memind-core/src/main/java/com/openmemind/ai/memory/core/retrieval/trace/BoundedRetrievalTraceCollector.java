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

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class BoundedRetrievalTraceCollector implements RetrievalTraceCollector {

    private final String traceId;
    private final Instant startedAt;
    private final RetrievalTraceOptions options;
    private final Queue<RetrievalStageTrace> stages = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean truncated = new AtomicBoolean();
    private final AtomicReference<RetrievalMergeTrace> merge = new AtomicReference<>();
    private final AtomicReference<RetrievalFinalTrace> finalResults = new AtomicReference<>();

    public BoundedRetrievalTraceCollector(RetrievalTraceOptions options) {
        this(UUID.randomUUID().toString(), Instant.now(), options);
    }

    public BoundedRetrievalTraceCollector(
            String traceId, Instant startedAt, RetrievalTraceOptions options) {
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.options = options == null ? RetrievalTraceOptions.defaults() : options;
    }

    @Override
    public void stageCompleted(RetrievalStageTrace event) {
        if (event == null) {
            return;
        }
        if (stages.size() >= options.maxStages()) {
            truncated.set(true);
            return;
        }
        stages.add(event);
    }

    @Override
    public void mergeCompleted(RetrievalMergeTrace event) {
        if (event != null) {
            merge.set(event);
        }
    }

    @Override
    public void finalResults(RetrievalFinalTrace event) {
        if (event != null) {
            finalResults.set(event);
        }
    }

    @Override
    public Optional<RetrievalDebugTrace> snapshot() {
        List<RetrievalStageTrace> orderedStages =
                stages.stream()
                        .sorted(Comparator.comparing(RetrievalStageTrace::startedAt))
                        .toList();
        return Optional.of(
                new RetrievalDebugTrace(
                        traceId,
                        startedAt,
                        Instant.now(),
                        truncated.get(),
                        orderedStages,
                        merge.get(),
                        finalResults.get()));
    }

    public RetrievalTraceOptions options() {
        return options;
    }
}
