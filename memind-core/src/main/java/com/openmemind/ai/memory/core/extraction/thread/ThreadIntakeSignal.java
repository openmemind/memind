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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured semantic input for thread materialization.
 */
public record ThreadIntakeSignal(
        String memoryId,
        long triggerItemId,
        String content,
        Instant eventTime,
        MemoryThreadType threadType,
        List<AnchorCandidate> anchorCandidates,
        ThreadEligibilityScore eligibility,
        List<Long> supportingItemIds,
        List<SemanticMarker> semanticMarkers,
        List<CanonicalRef> canonicalRefs,
        double confidence) {

    public ThreadIntakeSignal {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(eventTime, "eventTime");
        threadType = Objects.requireNonNull(threadType, "threadType");
        anchorCandidates = anchorCandidates == null ? List.of() : List.copyOf(anchorCandidates);
        eligibility = Objects.requireNonNull(eligibility, "eligibility");
        supportingItemIds = supportingItemIds == null ? List.of() : List.copyOf(supportingItemIds);
        semanticMarkers = semanticMarkers == null ? List.of() : List.copyOf(semanticMarkers);
        canonicalRefs = canonicalRefs == null ? List.of() : List.copyOf(canonicalRefs);
        if (triggerItemId <= 0) {
            throw new IllegalArgumentException("triggerItemId must be positive");
        }
        if (Double.isNaN(confidence) || confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be in [0,1]");
        }
    }

    public static ThreadIntakeSignal relationship(
            String memoryId,
            long triggerItemId,
            String content,
            List<String> participants,
            Instant eventTime) {
        return new ThreadIntakeSignal(
                memoryId,
                triggerItemId,
                content,
                eventTime,
                MemoryThreadType.RELATIONSHIP,
                List.of(new AnchorCandidate("relationship", null, participants, 1.0d)),
                new ThreadEligibilityScore(1.0d, 1.0d, 0.20d),
                List.of(triggerItemId),
                List.of(),
                List.of(),
                1.0d);
    }

    public boolean hasSemanticMarkers() {
        return !semanticMarkers.isEmpty();
    }

    public record AnchorCandidate(
            String anchorKind, String anchorKey, List<String> participants, double score) {

        public AnchorCandidate {
            Objects.requireNonNull(anchorKind, "anchorKind");
            participants = participants == null ? List.of() : List.copyOf(participants);
            if (Double.isNaN(score) || score < 0.0d || score > 1.0d) {
                throw new IllegalArgumentException("score must be in [0,1]");
            }
        }
    }

    public record ThreadEligibilityScore(
            double anchorability, double continuity, double statefulness) {

        public ThreadEligibilityScore {
            validateComponent(anchorability, "anchorability");
            validateComponent(continuity, "continuity");
            validateComponent(statefulness, "statefulness");
        }

        public double scoreFor(MemoryThreadType threadType) {
            Objects.requireNonNull(threadType, "threadType");
            double score =
                    switch (threadType) {
                        case WORK ->
                                anchorability * 0.50d + continuity * 0.15d + statefulness * 0.35d;
                        case CASE ->
                                anchorability * 0.45d + continuity * 0.10d + statefulness * 0.45d;
                        case RELATIONSHIP ->
                                anchorability * 0.25d + continuity * 0.60d + statefulness * 0.15d;
                        case TOPIC ->
                                anchorability * 0.45d + continuity * 0.45d + statefulness * 0.10d;
                    };
            return Math.max(0.0d, Math.min(1.0d, score));
        }

        private static void validateComponent(double value, String field) {
            if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
                throw new IllegalArgumentException(field + " must be in [0,1]");
            }
        }
    }

    public record SemanticMarker(
            MemoryThreadEventType eventType, String summary, Map<String, Object> attributes) {

        public SemanticMarker {
            eventType = Objects.requireNonNull(eventType, "eventType");
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }

        public static SemanticMarker of(
                MemoryThreadEventType eventType, String summary, Map<String, Object> attributes) {
            return new SemanticMarker(eventType, summary, attributes);
        }

        public String attribute(String key) {
            Object value = attributes.get(key);
            return value == null ? null : String.valueOf(value);
        }

        public String semanticFingerprint() {
            return firstNonBlank(
                    attribute("state"),
                    attribute("toState"),
                    attribute("blockerKey"),
                    attribute("decisionKey"),
                    attribute("questionKey"),
                    attribute("milestoneKey"),
                    attribute("resolutionKey"));
        }
    }

    public record CanonicalRef(String refType, String refKey) {

        public CanonicalRef {
            Objects.requireNonNull(refType, "refType");
            Objects.requireNonNull(refKey, "refKey");
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
