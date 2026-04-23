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
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Structural reducer that derives projection state from normalized events.
 */
public final class ThreadStructuralReducer {

    private final ThreadMaterializationPolicy policy;

    public ThreadStructuralReducer(ThreadMaterializationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public MemoryThreadProjection reduce(
            MemoryThreadProjection current,
            List<MemoryThreadEvent> orderedEvents,
            List<MemoryThreadMembership> memberships) {
        return reduce(current, orderedEvents, memberships, Instant.now());
    }

    public MemoryThreadProjection reduce(
            MemoryThreadProjection current,
            List<MemoryThreadEvent> orderedEvents,
            List<MemoryThreadMembership> memberships,
            Instant now) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(now, "now");
        List<MemoryThreadEvent> events =
                orderedEvents == null ? List.of() : List.copyOf(orderedEvents);
        List<MemoryThreadMembership> memberRows =
                memberships == null ? List.of() : List.copyOf(memberships);

        ReducerState state = new ReducerState(current);
        events.forEach(state::apply);

        Instant openedAt = events.isEmpty() ? current.openedAt() : events.getFirst().eventTime();
        Instant lastEventAt =
                events.isEmpty() ? current.lastEventAt() : events.getLast().eventTime();
        Instant lastMeaningfulUpdateAt = state.lastMeaningfulUpdateAt;

        MemoryThreadObjectState objectState = state.objectState();
        MemoryThreadLifecycleStatus lifecycleStatus =
                state.lifecycleStatus(objectState, lastEventAt, now, policy);
        Instant closedAt =
                lifecycleStatus == MemoryThreadLifecycleStatus.CLOSED
                        ? (state.resolvedAt != null ? state.resolvedAt : lastEventAt)
                        : null;
        String headline =
                state.latestSummary(
                        ThreadHeadlineFormatter.format(current.anchorKind(), current.anchorKey()),
                        current.headline());

        return new MemoryThreadProjection(
                current.memoryId(),
                current.threadKey(),
                current.threadType(),
                current.anchorKind(),
                current.anchorKey(),
                current.displayLabel(),
                lifecycleStatus,
                objectState,
                headline,
                state.snapshot(objectState, headline, memberRows.size()),
                1,
                openedAt,
                lastEventAt,
                lastMeaningfulUpdateAt,
                closedAt,
                events.size(),
                memberRows.size(),
                current.createdAt(),
                lastEventAt != null ? lastEventAt : current.updatedAt());
    }

    private static final class ReducerState {

        private final List<String> salientFacts = new ArrayList<>();
        private final Set<String> openBlockers = new LinkedHashSet<>();
        private final MemoryThreadProjection current;
        private String latestSemanticSummary;
        private String latestRawSummary;
        private Instant lastMeaningfulUpdateAt;
        private Instant resolvedAt;
        private boolean contradictedAfterResolution;
        private Map<String, Object> latestEvidence;
        private Map<String, Object> latestEnrichment;

        private ReducerState(MemoryThreadProjection current) {
            this.current = current;
            this.latestRawSummary = current.headline();
        }

        private void apply(MemoryThreadEvent event) {
            String summary = summary(event);
            if (summary != null && !summary.isBlank()) {
                latestRawSummary = summary;
                if (isSemanticHeadline(event)) {
                    latestSemanticSummary = summary;
                }
                if (!isHeadlineRefresh(event) && salientFacts.size() < 5) {
                    salientFacts.add(summary);
                }
            }
            if (event.meaningful()) {
                lastMeaningfulUpdateAt = event.eventTime();
                if (resolvedAt != null
                        && event.eventType() != MemoryThreadEventType.RESOLUTION_DECLARED) {
                    contradictedAfterResolution = true;
                }
            }
            Map<String, Object> evidence = evidence(event);
            if (evidence != null) {
                latestEvidence = evidence;
            }
            Map<String, Object> enrichment = enrichment(event);
            if (enrichment != null) {
                latestEnrichment = enrichment;
            }
            switch (event.eventType()) {
                case BLOCKER_ADDED -> openBlockers.add(key(event, "blockerKey"));
                case BLOCKER_CLEARED -> openBlockers.remove(key(event, "blockerKey"));
                case RESOLUTION_DECLARED -> {
                    resolvedAt = event.eventTime();
                    contradictedAfterResolution = false;
                }
                default -> {}
            }
        }

        private MemoryThreadObjectState objectState() {
            if (resolvedAt != null && !contradictedAfterResolution) {
                return MemoryThreadObjectState.RESOLVED;
            }
            if (!openBlockers.isEmpty()) {
                return MemoryThreadObjectState.BLOCKED;
            }
            if (lastMeaningfulUpdateAt != null) {
                return MemoryThreadObjectState.ONGOING;
            }
            if (current.lastEventAt() != null || current.lastMeaningfulUpdateAt() != null) {
                return MemoryThreadObjectState.STABLE;
            }
            return MemoryThreadObjectState.UNCERTAIN;
        }

        private MemoryThreadLifecycleStatus lifecycleStatus(
                MemoryThreadObjectState objectState,
                Instant lastEventAt,
                Instant now,
                ThreadMaterializationPolicy policy) {
            if (objectState == MemoryThreadObjectState.RESOLVED) {
                return MemoryThreadLifecycleStatus.CLOSED;
            }
            if (lastEventAt == null) {
                return current.lifecycleStatus();
            }
            Instant dormantCutoff = now.minus(policy.dormantAfter());
            Instant closeCutoff = now.minus(policy.closeAfter());
            if (lastEventAt.isBefore(closeCutoff)) {
                return MemoryThreadLifecycleStatus.CLOSED;
            }
            if (lastEventAt.isBefore(dormantCutoff)) {
                return MemoryThreadLifecycleStatus.DORMANT;
            }
            return MemoryThreadLifecycleStatus.ACTIVE;
        }

        private String latestSummary(String deterministicFallback, String rawFallback) {
            if (latestSemanticSummary != null && !latestSemanticSummary.isBlank()) {
                return latestSemanticSummary;
            }
            if (deterministicFallback != null && !deterministicFallback.isBlank()) {
                return deterministicFallback;
            }
            if (latestRawSummary != null && !latestRawSummary.isBlank()) {
                return latestRawSummary;
            }
            return rawFallback;
        }

        private Map<String, Object> snapshot(
                MemoryThreadObjectState objectState, String headline, int memberCount) {
            LinkedHashMap<String, Object> facets = new LinkedHashMap<>();
            if (!openBlockers.isEmpty()) {
                facets.put("openBlockers", List.copyOf(openBlockers));
            }
            if (latestEvidence != null) {
                facets.put("evidence", latestEvidence);
            }
            if (latestEnrichment != null) {
                facets.put("enrichment", latestEnrichment);
            }
            facets.put("memberCount", memberCount);

            LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("currentState", objectState.name());
            snapshot.put("latestUpdate", headline);
            snapshot.put("salientFacts", List.copyOf(salientFacts));
            snapshot.put("facets", Map.copyOf(facets));
            return Map.copyOf(snapshot);
        }

        private static String key(MemoryThreadEvent event, String field) {
            Object value = event.eventPayloadJson().get(field);
            return value == null ? event.eventKey() : String.valueOf(value);
        }

        private static String summary(MemoryThreadEvent event) {
            Object value = event.eventPayloadJson().get("summary");
            return value == null ? null : String.valueOf(value);
        }

        private static Map<String, Object> evidence(MemoryThreadEvent event) {
            Object value = event.eventPayloadJson().get("evidence");
            if (!(value instanceof Map<?, ?> rawEvidence)) {
                return null;
            }
            LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
            Object supportCount = rawEvidence.get("supportCount");
            if (supportCount instanceof Number number) {
                evidence.put("supportCount", number.intValue());
            }
            Object dominantFamilies = rawEvidence.get("dominantFamilies");
            if (dominantFamilies instanceof List<?> families) {
                evidence.put(
                        "dominantFamilies",
                        families.stream().map(String::valueOf).toList());
            }
            return evidence.isEmpty() ? null : Map.copyOf(evidence);
        }

        private static boolean isHeadlineRefresh(MemoryThreadEvent event) {
            Object value = event.eventPayloadJson().get("summaryRole");
            return value != null && "HEADLINE_REFRESH".equals(String.valueOf(value));
        }

        private static boolean isSemanticHeadline(MemoryThreadEvent event) {
            return isHeadlineRefresh(event)
                    || event.meaningful()
                    || hasSemanticField(event, "state")
                    || hasSemanticField(event, "fromState")
                    || hasSemanticField(event, "toState")
                    || hasSemanticField(event, "blockerKey")
                    || hasSemanticField(event, "decisionKey")
                    || hasSemanticField(event, "questionKey")
                    || hasSemanticField(event, "milestoneKey")
                    || hasSemanticField(event, "resolutionKey");
        }

        private static boolean hasSemanticField(MemoryThreadEvent event, String field) {
            Object value = event.eventPayloadJson().get(field);
            return value != null && !String.valueOf(value).isBlank();
        }

        private static Map<String, Object> enrichment(MemoryThreadEvent event) {
            Object provenanceValue = event.eventPayloadJson().get("provenance");
            if (!(provenanceValue instanceof Map<?, ?> provenanceMap)) {
                return null;
            }
            Object sourceType = provenanceMap.get("sourceType");
            if (!"THREAD_LLM".equals(String.valueOf(sourceType))) {
                return null;
            }

            LinkedHashMap<String, Object> enrichment = new LinkedHashMap<>();
            Object meaningfulCount =
                    event.eventPayloadJson().get("enrichedAgainstMeaningfulEventCount");
            if (meaningfulCount instanceof Number number) {
                enrichment.put("lastEnrichedMeaningfulEventCount", number.longValue());
            }
            if (event.createdAt() != null) {
                enrichment.put("lastEnrichedAt", event.createdAt().toString());
            }
            enrichment.put("headlineSource", "THREAD_LLM");
            return Map.copyOf(enrichment);
        }
    }
}
