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
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryThreadRuntimeStatus;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
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
    private final ThreadProjectionStore store;
    private final ThreadIntakeWorker worker;
    private final ThreadProjectionRebuilder rebuilder;
    private final MemoryThreadOptions options;
    private final ThreadMaterializationPolicy policy = ThreadMaterializationPolicy.v1();

    public MemoryThreadLayer(
            MemoryItemExtractStep delegate,
            ThreadProjectionStore store,
            ThreadIntakeWorker worker,
            MemoryThreadOptions options) {
        this(delegate, store, worker, null, options);
    }

    public MemoryThreadLayer(
            MemoryItemExtractStep delegate,
            ThreadProjectionStore store,
            ThreadIntakeWorker worker,
            ThreadProjectionRebuilder rebuilder,
            MemoryThreadOptions options) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.store = Objects.requireNonNull(store, "store");
        this.worker = worker;
        this.rebuilder = rebuilder;
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public Mono<MemoryItemResult> extract(
            MemoryId memoryId, RawDataResult rawDataResult, ItemExtractionConfig config) {
        return delegate.extract(memoryId, rawDataResult, config)
                .doOnNext(result -> enqueueCommittedItems(memoryId, result));
    }

    public void flush(MemoryId memoryId) {
        if (!options.enabled() || !options.derivation().enabled() || worker == null) {
            return;
        }
        worker.wake(memoryId);
    }

    public void rebuild(MemoryId memoryId) {
        if (!options.enabled() || rebuilder == null) {
            return;
        }
        rebuilder.rebuild(memoryId);
    }

    public MemoryThreadRuntimeStatus getThreadRuntimeStatus(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (!options.enabled()) {
            return MemoryThreadRuntimeStatus.disabled("memoryThread disabled");
        }
        if (!options.derivation().enabled()) {
            return MemoryThreadRuntimeStatus.disabled("memoryThread derivation disabled");
        }
        store.ensureRuntime(memoryId, policy.version());
        return MemoryThreadRuntimeStatus.fromRuntimeState(
                store.getRuntime(memoryId).orElse(null), true, true, null);
    }

    public MemoryThreadRuntimeStatus status() {
        if (!options.enabled()) {
            return MemoryThreadRuntimeStatus.disabled("memoryThread disabled");
        }
        return MemoryThreadRuntimeStatus.disabled("memoryId required");
    }

    @Override
    public void close() {}

    private void enqueueCommittedItems(MemoryId memoryId, MemoryItemResult result) {
        if (!options.enabled() || !options.derivation().enabled() || result.isEmpty()) {
            return;
        }
        boolean hasCommittedItems = false;
        store.ensureRuntime(memoryId, policy.version());
        for (MemoryItem item : result.newItems()) {
            if (item == null || item.id() == null) {
                continue;
            }
            hasCommittedItems = true;
            try {
                store.enqueue(memoryId, item.id());
            } catch (RuntimeException e) {
                store.markRebuildRequired(memoryId, "enqueue failure");
                log.warn(
                        "Failed to enqueue memory-thread intake for memoryId={} itemId={}",
                        memoryId,
                        item.id(),
                        e);
                return;
            }
        }
        if (hasCommittedItems && worker != null) {
            worker.wake(memoryId);
        }
    }
}
