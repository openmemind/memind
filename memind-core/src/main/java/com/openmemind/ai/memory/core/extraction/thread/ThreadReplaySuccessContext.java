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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ThreadReplaySuccessContext(
        MemoryId memoryId,
        ThreadReplayOrigin replayOrigin,
        long replayCutoffItemId,
        List<Long> coveredTriggerItemIds,
        List<MemoryThreadProjection> threads,
        List<MemoryThreadEvent> events,
        List<MemoryThreadMembership> memberships,
        String materializationPolicyVersion,
        Instant finalizedAt) {

    public ThreadReplaySuccessContext {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(replayOrigin, "replayOrigin");
        coveredTriggerItemIds = List.copyOf(Objects.requireNonNull(coveredTriggerItemIds, "coveredTriggerItemIds"));
        threads = List.copyOf(Objects.requireNonNull(threads, "threads"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        memberships = List.copyOf(Objects.requireNonNull(memberships, "memberships"));
        Objects.requireNonNull(materializationPolicyVersion, "materializationPolicyVersion");
        Objects.requireNonNull(finalizedAt, "finalizedAt");
    }
}
