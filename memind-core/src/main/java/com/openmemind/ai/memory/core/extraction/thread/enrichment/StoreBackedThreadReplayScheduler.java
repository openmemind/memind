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
package com.openmemind.ai.memory.core.extraction.thread.enrichment;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.thread.ThreadWakeScheduler;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schedules a wake-up after durable replay work has already been recorded in the store.
 */
public final class StoreBackedThreadReplayScheduler implements ThreadReplayScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(StoreBackedThreadReplayScheduler.class);

    private final ThreadProjectionStore projectionStore;
    private final ThreadWakeScheduler wakeScheduler;

    public StoreBackedThreadReplayScheduler(
            ThreadProjectionStore projectionStore, ThreadWakeScheduler wakeScheduler) {
        this.projectionStore = Objects.requireNonNull(projectionStore, "projectionStore");
        this.wakeScheduler = Objects.requireNonNull(wakeScheduler, "wakeScheduler");
    }

    @Override
    public void scheduleReplay(MemoryId memoryId, long replayCutoffItemId) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (replayCutoffItemId <= 0L) {
            throw new IllegalArgumentException("replayCutoffItemId must be positive");
        }
        if (projectionStore.listOutbox(memoryId).stream()
                .noneMatch(entry -> entry.triggerItemId() == replayCutoffItemId)) {
            return;
        }
        try {
            wakeScheduler.schedule(memoryId);
        } catch (RuntimeException error) {
            log.warn(
                    "Failed to submit asynchronous thread replay wake for memoryId={} cutoff={}",
                    memoryId,
                    replayCutoffItemId,
                    error);
        }
    }
}
