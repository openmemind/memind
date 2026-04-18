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
package com.openmemind.ai.memory.core.extraction.thread;

import com.openmemind.ai.memory.core.builder.MemoryThreadOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryThreadRuntimeStatus;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.thread.scheduler.MemoryThreadBuildScheduler;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Post-item-commit decorator that schedules deterministic memory-thread derivation.
 */
public class MemoryThreadLayer implements MemoryItemExtractStep, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MemoryThreadLayer.class);

    private final MemoryItemExtractStep delegate;
    private final MemoryThreadBuildScheduler scheduler;
    private final MemoryThreadOptions options;

    public MemoryThreadLayer(
            MemoryItemExtractStep delegate,
            MemoryThreadBuildScheduler scheduler,
            MemoryThreadOptions options) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public Mono<MemoryItemResult> extract(
            MemoryId memoryId, RawDataResult rawDataResult, ItemExtractionConfig config) {
        return delegate.extract(memoryId, rawDataResult, config)
                .doOnNext(result -> scheduleDerivation(memoryId, result, config));
    }

    public void flush(MemoryId memoryId) {
        scheduler.flush(memoryId);
    }

    public void rebuild(MemoryId memoryId) {
        scheduler.rebuild(memoryId);
    }

    public MemoryThreadRuntimeStatus status() {
        return scheduler.status();
    }

    @Override
    public void close() throws Exception {
        scheduler.close();
    }

    private void scheduleDerivation(
            MemoryId memoryId, MemoryItemResult result, ItemExtractionConfig config) {
        if (!options.enabled() || !options.derivation().enabled() || result.isEmpty()) {
            return;
        }
        try {
            scheduler.submitDerivation(memoryId, List.copyOf(result.newItems()), config);
        } catch (RuntimeException e) {
            log.warn("Failed to schedule memory-thread derivation for memoryId={}", memoryId, e);
        }
    }
}
