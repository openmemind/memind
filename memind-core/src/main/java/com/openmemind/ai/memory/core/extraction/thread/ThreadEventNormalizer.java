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

import com.openmemind.ai.memory.core.data.thread.MemoryThreadEnrichmentInput;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        return normalize(decision, signal, ThreadAdmissionEvidence.none());
    }

    public List<MemoryThreadEvent> normalize(
            ThreadDecision decision,
            ThreadIntakeSignal signal,
            ThreadAdmissionEvidence evidence) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(evidence, "evidence");
        if (decision.action() == ThreadDecision.Action.IGNORE) {
            return List.of();
        }

        List<ThreadIntakeSignal.SemanticMarker> markers =
                signal.hasSemanticMarkers()
                        ? signal.semanticMarkers()
                        : List.of(
                                ThreadIntakeSignal.SemanticMarker.of(
                                        defaultEventType(signal), signal.content(), Map.of()));

        return markers.stream().map(marker -> toEvent(decision, signal, marker, evidence)).toList();
    }

    public Optional<MemoryThreadEvent> normalizeEnrichment(
            MemoryThreadEnrichmentInput input,
            Map<String, MemoryThreadEvent> itemBackedEventsByKey,
            Map<Long, Instant> itemBackedEventTimeByItemId) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(itemBackedEventsByKey, "itemBackedEventsByKey");
        Objects.requireNonNull(itemBackedEventTimeByItemId, "itemBackedEventTimeByItemId");

        String eventTypeText = stringValue(input.payloadJson().get("eventType"));
        String basisEventKey = stringValue(input.payloadJson().get("basisEventKey"));
        if (eventTypeText == null || basisEventKey == null) {
            return Optional.empty();
        }
        MemoryThreadEvent basisEvent = itemBackedEventsByKey.get(basisEventKey);
        if (basisEvent == null) {
            return Optional.empty();
        }
        MemoryThreadEventType eventType = eventType(eventTypeText);
        if (eventType == null) {
            return Optional.empty();
        }

        List<Long> supportingItemIds = supportingItemIds(input.provenanceJson());
        Instant eventTime = anchoredEventTime(supportingItemIds, itemBackedEventTimeByItemId);
        if (eventTime == null) {
            eventTime = basisEvent.eventTime();
        }
        if (eventTime == null) {
            return Optional.empty();
        }

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(input.payloadJson());
        payload.remove("eventType");
        payload.remove("meaningful");
        payload.put("basisEventKey", basisEventKey);
        payload.put("enrichedAgainstMeaningfulEventCount", input.basisMeaningfulEventCount());
        payload.put("provenance", provenance(input.provenanceJson(), supportingItemIds));

        return Optional.of(
                new MemoryThreadEvent(
                        input.memoryId(),
                        input.threadKey(),
                        enrichmentEventKey(input),
                        UNASSIGNED_EVENT_SEQ,
                        eventType,
                        eventTime,
                        Map.copyOf(payload),
                        1,
                        booleanValue(input.payloadJson().get("meaningful")),
                        null,
                        input.createdAt()));
    }

    private MemoryThreadEvent toEvent(
            ThreadDecision decision,
            ThreadIntakeSignal signal,
            ThreadIntakeSignal.SemanticMarker marker,
            ThreadAdmissionEvidence evidence) {
        return new MemoryThreadEvent(
                signal.memoryId(),
                decision.threadKey(),
                eventKey(decision.threadKey(), signal.triggerItemId(), marker),
                UNASSIGNED_EVENT_SEQ,
                marker.eventType(),
                signal.eventTime(),
                payload(signal, marker, evidence),
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
            ThreadIntakeSignal signal,
            ThreadIntakeSignal.SemanticMarker marker,
            ThreadAdmissionEvidence evidence) {
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
        if (!evidence.isEmpty()) {
            payload.put(
                    "evidence",
                    Map.of(
                            "supportCount",
                            evidence.supportCount(),
                            "dominantFamilies",
                            evidence.dominantFamilies()));
        }
        payload.put(
                "sources", List.of(Map.of("sourceType", "ITEM", "itemId", signal.triggerItemId())));
        return Map.copyOf(payload);
    }

    private static void copyIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }

    private static String enrichmentEventKey(MemoryThreadEnrichmentInput input) {
        return input.threadKey() + ":enrichment:" + input.inputRunKey() + ":" + input.entrySeq();
    }

    private static MemoryThreadEventType eventType(String raw) {
        try {
            return MemoryThreadEventType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    private static List<Long> supportingItemIds(Map<String, Object> provenanceJson) {
        Object supportingItemIds = provenanceJson == null ? null : provenanceJson.get("supportingItemIds");
        if (!(supportingItemIds instanceof List<?> rawIds)) {
            return List.of();
        }
        List<Long> normalized = new ArrayList<>();
        for (Object rawId : rawIds) {
            if (rawId instanceof Number number) {
                normalized.add(number.longValue());
                continue;
            }
            if (rawId instanceof String text && !text.isBlank()) {
                try {
                    normalized.add(Long.parseLong(text));
                } catch (RuntimeException ignored) {
                    // Ignore malformed supporting item ids rather than failing replay.
                }
            }
        }
        return List.copyOf(normalized);
    }

    private static Instant anchoredEventTime(
            List<Long> supportingItemIds, Map<Long, Instant> itemBackedEventTimeByItemId) {
        Instant anchored = null;
        for (Long supportingItemId : supportingItemIds) {
            Instant candidate = itemBackedEventTimeByItemId.get(supportingItemId);
            if (candidate == null) {
                continue;
            }
            if (anchored == null || candidate.isAfter(anchored)) {
                anchored = candidate;
            }
        }
        return anchored;
    }

    private static Map<String, Object> provenance(
            Map<String, Object> provenanceJson, List<Long> supportingItemIds) {
        LinkedHashMap<String, Object> provenance =
                new LinkedHashMap<>(provenanceJson == null ? Map.of() : provenanceJson);
        provenance.put("sourceType", "THREAD_LLM");
        if (!supportingItemIds.isEmpty()) {
            provenance.put("supportingItemIds", supportingItemIds);
        }
        return Map.copyOf(provenance);
    }
}
