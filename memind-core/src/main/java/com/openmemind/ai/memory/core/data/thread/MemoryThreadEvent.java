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
package com.openmemind.ai.memory.core.data.thread;

import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized thread event row.
 */
public record MemoryThreadEvent(
        String memoryId,
        String threadKey,
        String eventKey,
        long eventSeq,
        MemoryThreadEventType eventType,
        Instant eventTime,
        Map<String, Object> eventPayloadJson,
        int eventPayloadVersion,
        boolean meaningful,
        Double confidence,
        Instant createdAt) {

    public MemoryThreadEvent {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(threadKey, "threadKey");
        Objects.requireNonNull(eventKey, "eventKey");
        eventType = Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(eventTime, "eventTime");
        eventPayloadJson = eventPayloadJson == null ? Map.of() : Map.copyOf(eventPayloadJson);
        if (eventSeq < 0) {
            throw new IllegalArgumentException("eventSeq must be non-negative");
        }
        if (eventPayloadVersion < 0) {
            throw new IllegalArgumentException("eventPayloadVersion must be non-negative");
        }
        if (confidence != null
                && (Double.isNaN(confidence) || confidence < 0.0d || confidence > 1.0d)) {
            throw new IllegalArgumentException("confidence must be in [0,1]");
        }
    }
}
