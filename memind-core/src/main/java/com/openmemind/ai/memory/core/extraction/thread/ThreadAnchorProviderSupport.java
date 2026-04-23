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

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.extraction.thread.marker.ThreadSemanticMarker;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ThreadAnchorProviderSupport {

    private static final Set<String> WORK_REF_TYPES = Set.of("project", "initiative", "workstream");
    private static final Set<String> CASE_REF_TYPES =
            Set.of("case", "incident", "ticket", "bug", "investigation");
    private static final Set<MemoryThreadEventType> MEANINGFUL_MARKER_TYPES =
            Set.of(
                    MemoryThreadEventType.STATE_CHANGE,
                    MemoryThreadEventType.BLOCKER_ADDED,
                    MemoryThreadEventType.BLOCKER_CLEARED,
                    MemoryThreadEventType.DECISION_MADE,
                    MemoryThreadEventType.MILESTONE_REACHED,
                    MemoryThreadEventType.RESOLUTION_DECLARED,
                    MemoryThreadEventType.SETBACK);

    private static final ThreadAnchorCanonicalizer CANONICALIZER =
            new ThreadAnchorCanonicalizer();

    private ThreadAnchorProviderSupport() {}

    static Set<String> normalizedEntityKeys(ThreadAnchorContext context) {
        Set<String> entityKeys = new LinkedHashSet<>();
        for (ItemEntityMention mention : context.mentions()) {
            if (Objects.equals(context.triggerItem().id(), mention.itemId())) {
                String normalized = ThreadAnchorCanonicalizer.normalizeToken(mention.entityKey());
                if (normalized != null) {
                    entityKeys.add(normalized);
                }
            }
        }
        return entityKeys;
    }

    static List<String> relationshipParticipants(ThreadAnchorContext context) {
        return normalizedEntityKeys(context).stream()
                .filter(ThreadAnchorProviderSupport::isRelationshipParticipant)
                .sorted()
                .toList();
    }

    static List<ThreadIntakeSignal.SemanticMarker> parseMarkers(
            ThreadSemanticMarker.SemanticsEnvelope semantics) {
        List<ThreadSemanticMarker> markers = semantics == null ? List.of() : semantics.markers();
        if (markers == null || markers.isEmpty()) {
            return List.of();
        }
        return markers.stream()
                .map(
                        marker ->
                                ThreadIntakeSignal.SemanticMarker.of(
                                        marker.eventType(),
                                        marker.objectRef(),
                                        marker.summary(),
                                        marker.attributes()))
                .toList();
    }

    static List<Long> parseContinuityTargets(ThreadSemanticMarker.SemanticsEnvelope semantics) {
        List<ThreadSemanticMarker.ContinuityLink> continuityLinks =
                semantics == null ? List.of() : semantics.continuityLinks();
        if (continuityLinks == null || continuityLinks.isEmpty()) {
            return List.of();
        }
        return continuityLinks.stream()
                .filter(link -> "continues".equalsIgnoreCase(link.linkType()))
                .map(ThreadSemanticMarker.ContinuityLink::targetItemId)
                .filter(Objects::nonNull)
                .filter(targetItemId -> targetItemId > 0L)
                .distinct()
                .sorted()
                .toList();
    }

    static List<ThreadIntakeSignal.SemanticMarker> unboundMarkers(
            List<ThreadIntakeSignal.SemanticMarker> markers) {
        return markers.stream()
                .filter(marker -> marker.objectRef() == null || marker.objectRef().isBlank())
                .toList();
    }

    static List<ThreadIntakeSignal.SemanticMarker> markersForAnchor(
            List<ThreadIntakeSignal.SemanticMarker> markers,
            MemoryThreadType threadType,
            String anchorKind,
            String anchorKey) {
        if (markers.isEmpty()) {
            return List.of();
        }
        return markers.stream()
                .filter(
                        marker ->
                                markerMatchesAnchor(
                                        marker.objectRef(), threadType, anchorKind, anchorKey))
                .toList();
    }

    static boolean hasBoundMeaningfulMarker(
            List<ThreadIntakeSignal.SemanticMarker> markers,
            MemoryThreadType threadType,
            String anchorKind,
            String anchorKey) {
        return markers.stream()
                .filter(marker -> MEANINGFUL_MARKER_TYPES.contains(marker.eventType()))
                .map(ThreadIntakeSignal.SemanticMarker::objectRef)
                .filter(Objects::nonNull)
                .anyMatch(
                        objectRef ->
                                markerMatchesAnchor(objectRef, threadType, anchorKind, anchorKey));
    }

    static List<MetadataAnchorCandidate> metadataAnchorCandidates(
            List<ThreadSemanticMarker.CanonicalRef> canonicalRefs) {
        if (canonicalRefs == null || canonicalRefs.isEmpty()) {
            return List.of();
        }
        Map<String, MetadataAnchorCandidate> byThreadKey = new LinkedHashMap<>();
        for (ThreadSemanticMarker.CanonicalRef canonicalRef : canonicalRefs) {
            metadataAnchor(canonicalRef.refType(), canonicalRef.refKey())
                    .ifPresent(
                            candidate -> byThreadKey.putIfAbsent(candidate.threadKey(), candidate));
        }
        return List.copyOf(byThreadKey.values());
    }

    static ThreadIntakeSignal.ThreadEligibilityScore metadataEligibility(
            MemoryItem item,
            MemoryThreadType threadType,
            List<ItemLink> adjacentLinks,
            List<EntityCooccurrence> cooccurrences,
            boolean boundMeaningfulMarker) {
        if (threadType == MemoryThreadType.WORK || threadType == MemoryThreadType.CASE) {
            return new ThreadIntakeSignal.ThreadEligibilityScore(
                    1.0d,
                    strongestNonEntityContinuity(adjacentLinks),
                    boundMeaningfulMarker ? 0.90d : 0.25d);
        }
        return new ThreadIntakeSignal.ThreadEligibilityScore(
                1.0d, topicContinuity(adjacentLinks, cooccurrences), statefulness(item));
    }

    static double strongestNonEntityContinuity(List<ItemLink> adjacentLinks) {
        double strongest =
                adjacentLinks.stream()
                        .map(ItemLink::strength)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(0.0d);
        return strongest > 0.0d ? strongest : 0.35d;
    }

    static double relationshipContinuity(
            List<String> participants,
            List<ItemLink> adjacentLinks,
            List<EntityCooccurrence> cooccurrences) {
        double linkStrength =
                adjacentLinks.stream()
                        .map(ItemLink::strength)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(0.0d);
        double cooccurrenceStrength =
                cooccurrences.stream()
                        .filter(
                                row ->
                                        participants.contains(row.leftEntityKey())
                                                && participants.contains(row.rightEntityKey()))
                        .mapToInt(EntityCooccurrence::cooccurrenceCount)
                        .max()
                        .orElse(0);
        double normalizedCooccurrence = Math.min(1.0d, cooccurrenceStrength / 3.0d);
        return Math.max(0.85d, Math.max(linkStrength, normalizedCooccurrence));
    }

    static double topicContinuity(
            List<ItemLink> adjacentLinks, List<EntityCooccurrence> cooccurrences) {
        double linkStrength =
                adjacentLinks.stream()
                        .map(ItemLink::strength)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(0.0d);
        int cooccurrenceStrength =
                cooccurrences.stream().mapToInt(EntityCooccurrence::cooccurrenceCount).max().orElse(0);
        return Math.max(0.75d, Math.max(linkStrength, Math.min(1.0d, cooccurrenceStrength / 4.0d)));
    }

    static double statefulness(MemoryItem item) {
        return item.category() == MemoryCategory.EVENT ? 0.90d : 0.20d;
    }

    static boolean hasEventLikeSignal(
            MemoryItem item, List<ThreadIntakeSignal.SemanticMarker> markers) {
        Objects.requireNonNull(item, "item");
        List<ThreadIntakeSignal.SemanticMarker> markerList =
                markers == null ? List.of() : markers;
        return item.category() == MemoryCategory.EVENT
                || markerList.stream()
                        .map(ThreadIntakeSignal.SemanticMarker::eventType)
                        .anyMatch(MEANINGFUL_MARKER_TYPES::contains);
    }

    private static boolean markerMatchesAnchor(
            String objectRef, MemoryThreadType threadType, String anchorKind, String anchorKey) {
        if (objectRef == null || objectRef.isBlank()) {
            return false;
        }
        int separator = objectRef.indexOf(':');
        if (separator <= 0 || separator == objectRef.length() - 1) {
            return false;
        }
        String refType = objectRef.substring(0, separator);
        String refKey = objectRef.substring(separator + 1);
        return metadataAnchor(refType, refKey)
                .filter(candidate -> candidate.threadType() == threadType)
                .filter(candidate -> candidate.anchorKind().equals(anchorKind))
                .filter(candidate -> candidate.anchorKey().equals(anchorKey))
                .isPresent();
    }

    private static java.util.Optional<MetadataAnchorCandidate> metadataAnchor(
            String refType, String refKey) {
        String normalizedType = ThreadAnchorCanonicalizer.normalizeToken(refType);
        String normalizedKey = ThreadAnchorCanonicalizer.normalizeToken(refKey);
        if (normalizedType == null || normalizedKey == null) {
            return java.util.Optional.empty();
        }
        if ("topic".equals(normalizedType)) {
            return java.util.Optional.of(
                    metadataAnchorCandidate(
                            MemoryThreadType.TOPIC,
                            "topic",
                            "concept:" + normalizedKey,
                            refType,
                            refKey));
        }
        if (WORK_REF_TYPES.contains(normalizedType)) {
            return java.util.Optional.of(
                    metadataAnchorCandidate(
                            MemoryThreadType.WORK,
                            normalizedType,
                            normalizedType + ":" + normalizedKey,
                            refType,
                            refKey));
        }
        if (CASE_REF_TYPES.contains(normalizedType)) {
            return java.util.Optional.of(
                    metadataAnchorCandidate(
                            MemoryThreadType.CASE,
                            normalizedType,
                            normalizedType + ":" + normalizedKey,
                            refType,
                            refKey));
        }
        return java.util.Optional.empty();
    }

    private static MetadataAnchorCandidate metadataAnchorCandidate(
            MemoryThreadType threadType,
            String anchorKind,
            String anchorKey,
            String rawRefType,
            String rawRefKey) {
        ThreadIntakeSignal.AnchorCandidate anchorCandidate =
                new ThreadIntakeSignal.AnchorCandidate(anchorKind, anchorKey, List.of(), 1.0d);
        String threadKey =
                CANONICALIZER.canonicalize(threadType, anchorCandidate).orElseThrow().threadKey();
        return new MetadataAnchorCandidate(
                threadType,
                anchorKind,
                anchorKey,
                threadKey,
                new ThreadIntakeSignal.CanonicalRef(rawRefType, rawRefKey));
    }

    record MetadataAnchorCandidate(
            MemoryThreadType threadType,
            String anchorKind,
            String anchorKey,
            String threadKey,
            ThreadIntakeSignal.CanonicalRef rawCanonicalRef) {}

    private static boolean isRelationshipParticipant(String entityKey) {
        return entityKey != null
                && (entityKey.startsWith("person:") || entityKey.startsWith("special:"));
    }
}
