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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Normalizes one intake signal into deterministic thread events.
 */
public final class ThreadEventNormalizer {

    private static final long UNASSIGNED_EVENT_SEQ = 0L;
    private static final Set<MemoryThreadEventType> MEANINGFUL_TYPES =
            Set.of(
                    MemoryThreadEventType.STATE_CHANGE,
                    MemoryThreadEventType.BLOCKER_ADDED,
                    MemoryThreadEventType.BLOCKER_CLEARED,
                    MemoryThreadEventType.DECISION_MADE,
                    MemoryThreadEventType.MILESTONE_REACHED,
                    MemoryThreadEventType.RESOLUTION_DECLARED,
                    MemoryThreadEventType.SETBACK);

    public List<MemoryThreadEvent> normalize(ThreadDecision decision, ThreadIntakeSignal signal) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(signal, "signal");
        if (decision.action() == ThreadDecision.Action.IGNORE) {
            return List.of();
        }

        List<ThreadIntakeSignal.SemanticMarker> markers =
                signal.hasSemanticMarkers()
                        ? signal.semanticMarkers()
                        : List.of(
                                ThreadIntakeSignal.SemanticMarker.of(
                                        defaultEventType(signal), signal.content(), Map.of()));

        return markers.stream().map(marker -> toEvent(decision, signal, marker)).toList();
    }

    private MemoryThreadEvent toEvent(
            ThreadDecision decision,
            ThreadIntakeSignal signal,
            ThreadIntakeSignal.SemanticMarker marker) {
        return new MemoryThreadEvent(
                signal.memoryId(),
                decision.threadKey(),
                eventKey(decision.threadKey(), signal.triggerItemId(), marker),
                UNASSIGNED_EVENT_SEQ,
                marker.eventType(),
                signal.eventTime(),
                payload(signal, marker),
                1,
                MEANINGFUL_TYPES.contains(marker.eventType()),
                signal.confidence(),
                signal.eventTime());
    }

    private static MemoryThreadEventType defaultEventType(ThreadIntakeSignal signal) {
        if ((signal.threadType() == MemoryThreadType.WORK
                        || signal.threadType() == MemoryThreadType.CASE)
                && signal.eligibility().statefulness() >= 0.75d) {
            return MemoryThreadEventType.UPDATE;
        }
        return MemoryThreadEventType.OBSERVATION;
    }

    private static String eventKey(
            String threadKey, long triggerItemId, ThreadIntakeSignal.SemanticMarker marker) {
        String base =
                threadKey
                        + ":"
                        + marker.eventType().name().toLowerCase(Locale.ROOT)
                        + ":"
                        + triggerItemId;
        String fingerprint = marker.semanticFingerprint();
        return fingerprint.isBlank() ? base : base + ":" + fingerprint;
    }

    private static Map<String, Object> payload(
            ThreadIntakeSignal signal, ThreadIntakeSignal.SemanticMarker marker) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        String summary =
                marker.summary() != null && !marker.summary().isBlank()
                        ? marker.summary()
                        : signal.content();
        payload.put("summary", summary);
        payload.put("facts", List.of());
        copyIfPresent(payload, "state", marker.attribute("state"));
        copyIfPresent(payload, "fromState", marker.attribute("fromState"));
        copyIfPresent(payload, "toState", marker.attribute("toState"));
        copyIfPresent(payload, "blockerKey", marker.attribute("blockerKey"));
        copyIfPresent(payload, "decisionKey", marker.attribute("decisionKey"));
        copyIfPresent(payload, "questionKey", marker.attribute("questionKey"));
        copyIfPresent(payload, "milestoneKey", marker.attribute("milestoneKey"));
        copyIfPresent(payload, "resolutionKey", marker.attribute("resolutionKey"));
        payload.put(
                "sources", List.of(Map.of("sourceType", "ITEM", "itemId", signal.triggerItemId())));
        return Map.copyOf(payload);
    }

    private static void copyIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }
}
