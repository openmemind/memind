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
package com.openmemind.ai.memory.core.store.thread;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEnrichmentInput;
import java.util.List;

/**
 * Disabled-mode authoritative enrichment input store.
 */
public final class NoOpThreadEnrichmentInputStore implements ThreadEnrichmentInputStore {

    public static final NoOpThreadEnrichmentInputStore INSTANCE =
            new NoOpThreadEnrichmentInputStore();

    private NoOpThreadEnrichmentInputStore() {}

    @Override
    public ThreadEnrichmentAppendResult appendRunAndEnqueueReplay(
            MemoryId memoryId,
            long replayCutoffItemId,
            List<MemoryThreadEnrichmentInput> runInputs) {
        return ThreadEnrichmentAppendResult.DUPLICATE_EQUIVALENT;
    }

    @Override
    public List<MemoryThreadEnrichmentInput> listReplayable(
            MemoryId memoryId, long cutoffItemId, String materializationPolicyVersion) {
        return List.of();
    }
}
