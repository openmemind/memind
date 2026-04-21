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
import java.util.Comparator;
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
        Objects.requireNonNull(current, "current");
        List<MemoryThreadEvent> events =
                (orderedEvents == null ? List.<MemoryThreadEvent>of() : orderedEvents)
                        .stream()
                                .sorted(
                                        Comparator.comparing(MemoryThreadEvent::eventTime)
                                                .thenComparing(MemoryThreadEvent::eventKey))
                                .toList();
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
                state.lifecycleStatus(objectState, lastEventAt);
        Instant closedAt =
                lifecycleStatus == MemoryThreadLifecycleStatus.CLOSED ? state.resolvedAt : null;
        String headline = state.latestSummary(current.headline());

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
        private String latestSummary;
        private Instant lastMeaningfulUpdateAt;
        private Instant resolvedAt;
        private boolean contradictedAfterResolution;

        private ReducerState(MemoryThreadProjection current) {
            this.current = current;
            this.latestSummary = current.headline();
        }

        private void apply(MemoryThreadEvent event) {
            String summary = summary(event);
            if (summary != null && !summary.isBlank()) {
                latestSummary = summary;
                if (salientFacts.size() < 5) {
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
                MemoryThreadObjectState objectState, Instant lastEventAt) {
            if (objectState == MemoryThreadObjectState.RESOLVED) {
                return MemoryThreadLifecycleStatus.CLOSED;
            }
            if (lastMeaningfulUpdateAt != null) {
                return MemoryThreadLifecycleStatus.ACTIVE;
            }
            if (lastEventAt != null) {
                return MemoryThreadLifecycleStatus.DORMANT;
            }
            return current.lifecycleStatus();
        }

        private String latestSummary(String fallback) {
            return latestSummary != null && !latestSummary.isBlank() ? latestSummary : fallback;
        }

        private Map<String, Object> snapshot(
                MemoryThreadObjectState objectState, String headline, int memberCount) {
            LinkedHashMap<String, Object> facets = new LinkedHashMap<>();
            if (!openBlockers.isEmpty()) {
                facets.put("openBlockers", List.copyOf(openBlockers));
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
    }
}
