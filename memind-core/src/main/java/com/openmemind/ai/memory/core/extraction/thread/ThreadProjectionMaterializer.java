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
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEnrichmentInput;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.thread.NoOpThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentInputStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic materialization helper shared by the incremental worker and the full rebuilder.
 */
class ThreadProjectionMaterializer {

    private final ItemOperations itemOperations;
    private final GraphOperations graphOperations;
    private final ThreadIntakeSignalExtractor signalExtractor;
    private final ThreadEventNormalizer eventNormalizer;
    private final ThreadStructuralReducer structuralReducer;
    private final ThreadCandidateCollector candidateCollector;
    private final ThreadCandidateScorer candidateScorer;
    private final ThreadDecisionEngine decisionEngine;
    private final ThreadAnchorCanonicalizer canonicalizer = new ThreadAnchorCanonicalizer();
    private final ThreadEnrichmentInputStore enrichmentInputStore;
    private final ThreadMaterializationPolicy policy;

    ThreadProjectionMaterializer(
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            ThreadMaterializationPolicy policy) {
        this(
                itemOperations,
                graphOperations,
                NoOpThreadEnrichmentInputStore.INSTANCE,
                policy,
                ThreadDerivationMetrics.NOOP);
    }

    ThreadProjectionMaterializer(
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            ThreadEnrichmentInputStore enrichmentInputStore,
            ThreadMaterializationPolicy policy) {
        this(itemOperations, graphOperations, enrichmentInputStore, policy, ThreadDerivationMetrics.NOOP);
    }

    ThreadProjectionMaterializer(
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            ThreadEnrichmentInputStore enrichmentInputStore,
            ThreadMaterializationPolicy policy,
            ThreadDerivationMetrics metrics) {
        this.itemOperations = Objects.requireNonNull(itemOperations, "itemOperations");
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
        this.enrichmentInputStore =
                Objects.requireNonNull(enrichmentInputStore, "enrichmentInputStore");
        this.policy = Objects.requireNonNull(policy, "policy");
        ThreadDerivationMetrics derivationMetrics =
                Objects.requireNonNull(metrics, "metrics");
        this.signalExtractor = new ThreadIntakeSignalExtractor(policy, derivationMetrics);
        this.eventNormalizer = new ThreadEventNormalizer();
        this.structuralReducer = new ThreadStructuralReducer(policy);
        this.candidateCollector = new ThreadCandidateCollector();
        this.candidateScorer = new ThreadCandidateScorer(policy);
        this.decisionEngine = new ThreadDecisionEngine(policy, derivationMetrics);
    }

    MaterializedProjection materializeUpTo(MemoryId memoryId, long cutoffItemId) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (cutoffItemId <= 0L) {
            return MaterializedProjection.empty();
        }
        Instant reductionNow = Instant.now();
        List<MemoryThreadEnrichmentInput> enrichmentInputs =
                enrichmentInputStore.listReplayable(memoryId, cutoffItemId, policy.version());

        List<MemoryItem> items =
                itemOperations.listItems(memoryId).stream()
                        .filter(item -> item.id() != null && item.id() <= cutoffItemId)
                        .sorted(Comparator.comparing(MemoryItem::id))
                        .toList();
        if (items.isEmpty()) {
            return MaterializedProjection.empty();
        }

        Set<Long> itemIds = items.stream().map(MemoryItem::id).collect(Collectors.toSet());
        Map<Long, List<ItemEntityMention>> mentionsByItemId =
                graphOperations.listItemEntityMentions(memoryId).stream()
                        .filter(mention -> itemIds.contains(mention.itemId()))
                        .collect(
                                Collectors.groupingBy(
                                        ItemEntityMention::itemId,
                                        LinkedHashMap::new,
                                        Collectors.toCollection(ArrayList::new)));
        Map<Long, List<ItemLink>> adjacentLinksByItemId = adjacentLinksByItemId(memoryId, itemIds);
        Map<Long, Set<String>> durableEntityKeysByItemId =
                durableEntityKeysByItemId(mentionsByItemId);
        List<EntityCooccurrence> cooccurrences = graphOperations.listEntityCooccurrences(memoryId);
        Map<Long, List<ThreadIntakeSignal>> signalsByItemId = new LinkedHashMap<>();
        CreationSupportState creationSupport = new CreationSupportState();

        Map<String, MemoryThreadProjection> projectionsByKey = new LinkedHashMap<>();
        Map<String, List<MemoryThreadEvent>> eventsByThreadKey = new LinkedHashMap<>();
        Map<String, List<MemoryThreadMembership>> membershipsByThreadKey = new LinkedHashMap<>();
        Map<Long, List<MemoryThreadMembership>> membershipsByItemId = new LinkedHashMap<>();
        Map<String, MemoryThreadEvent> itemBackedEventsByKey = new LinkedHashMap<>();
        Map<Long, Instant> itemBackedEventTimeByItemId = new LinkedHashMap<>();
        int enrichmentIndex = 0;

        for (MemoryItem item : items) {
            List<ThreadIntakeSignal> signals =
                    signalExtractor.extract(
                            item,
                            mentionsByItemId.getOrDefault(item.id(), List.of()),
                            adjacentLinksByItemId.getOrDefault(item.id(), List.of()),
                            cooccurrences);
            signalsByItemId.put(item.id(), signals);
            accumulateCreationSupport(signals, creationSupport);
        }

        for (MemoryItem item : items) {
            List<ThreadIntakeSignal> signals = signalsByItemId.getOrDefault(item.id(), List.of());
            for (Map.Entry<MemoryThreadType, List<CanonicalizedSignal>> entry :
                    canonicalSignalsByType(signals).entrySet()) {
                List<ThreadCandidate> candidates =
                        candidateCollector.collect(
                                item,
                                entry.getKey(),
                                entry.getValue(),
                                projectionsByKey,
                                membershipsByThreadKey,
                                membershipsByItemId,
                                adjacentLinksByItemId.getOrDefault(item.id(), List.of()));
                List<ThreadCandidateScore> scoredCandidates =
                        candidateScorer.score(
                                item,
                                candidates,
                                adjacentLinksByItemId.getOrDefault(item.id(), List.of()),
                                durableEntityKeysByItemId);
                ThreadDecisionOutcome outcome =
                        decisionEngine.resolve(
                                item,
                                entry.getKey(),
                                entry.getValue(),
                                scoredCandidates,
                                creationSupport);
                if (outcome.decision().action() == ThreadDecision.Action.IGNORE) {
                    continue;
                }
                ThreadDecision decision = outcome.decision();
                ThreadIntakeSignal signal = outcome.signal();
                MemoryThreadProjection baseProjection =
                        projectionsByKey.computeIfAbsent(
                                decision.threadKey(),
                                ignored -> seedProjection(decision, item, signal.eventTime()));
                List<MemoryThreadEvent> eventRows =
                        eventsByThreadKey.computeIfAbsent(
                                decision.threadKey(), ignored -> new ArrayList<>());
                List<MemoryThreadMembership> membershipRows =
                        membershipsByThreadKey.computeIfAbsent(
                                decision.threadKey(), ignored -> new ArrayList<>());

                long nextEventSeq =
                        eventRows.stream().mapToLong(MemoryThreadEvent::eventSeq).max().orElse(0L)
                                + 1L;
                for (MemoryThreadEvent event :
                        eventNormalizer.normalize(decision, signal, outcome.evidence())) {
                    MemoryThreadEvent sequenced = withEventSeq(event, nextEventSeq++);
                    eventRows.add(sequenced);
                    indexItemBackedEvent(
                            sequenced, itemBackedEventsByKey, itemBackedEventTimeByItemId);
                }
                if (!hasMembershipForThreadType(
                                item.id(), entry.getKey(), membershipsByItemId, projectionsByKey)
                        && membershipRows.stream()
                                .noneMatch(membership -> membership.itemId() == item.id())) {
                    MemoryThreadMembership membership =
                            membership(
                                    signal,
                                    decision,
                                    item.id(),
                                    outcome.role(),
                                    outcome.primary(),
                                    outcome.relevanceWeight(),
                                    outcome.evidence());
                    membershipRows.add(membership);
                    membershipsByItemId
                            .computeIfAbsent(item.id(), ignored -> new ArrayList<>())
                            .add(membership);
                }

                projectionsByKey.put(
                        decision.threadKey(),
                        structuralReducer.reduce(
                                baseProjection,
                                List.copyOf(eventRows),
                                List.copyOf(membershipRows),
                                reductionNow));
            }

            while (enrichmentIndex < enrichmentInputs.size()
                    && enrichmentInputs.get(enrichmentIndex).basisCutoffItemId() <= item.id()) {
                applyEnrichmentInput(
                        enrichmentInputs.get(enrichmentIndex),
                        itemBackedEventsByKey,
                        itemBackedEventTimeByItemId,
                        projectionsByKey,
                        eventsByThreadKey,
                        membershipsByThreadKey,
                        reductionNow);
                enrichmentIndex++;
            }
        }

        List<MemoryThreadProjection> threads =
                projectionsByKey.values().stream()
                        .sorted(Comparator.comparing(MemoryThreadProjection::threadKey))
                        .toList();
        List<MemoryThreadEvent> events =
                eventsByThreadKey.values().stream()
                        .flatMap(Collection::stream)
                        .sorted(
                                Comparator.comparing(MemoryThreadEvent::threadKey)
                                        .thenComparingLong(MemoryThreadEvent::eventSeq)
                                        .thenComparing(MemoryThreadEvent::eventKey))
                        .toList();
        List<MemoryThreadMembership> memberships =
                membershipsByThreadKey.values().stream()
                        .flatMap(Collection::stream)
                        .sorted(
                                Comparator.comparing(MemoryThreadMembership::threadKey)
                                        .thenComparingLong(MemoryThreadMembership::itemId)
                                        .thenComparing(MemoryThreadMembership::role))
                        .toList();

        return new MaterializedProjection(threads, events, memberships, items.getLast().id());
    }

    private void applyEnrichmentInput(
            MemoryThreadEnrichmentInput input,
            Map<String, MemoryThreadEvent> itemBackedEventsByKey,
            Map<Long, Instant> itemBackedEventTimeByItemId,
            Map<String, MemoryThreadProjection> projectionsByKey,
            Map<String, List<MemoryThreadEvent>> eventsByThreadKey,
            Map<String, List<MemoryThreadMembership>> membershipsByThreadKey,
            Instant reductionNow) {
        MemoryThreadProjection currentProjection = projectionsByKey.get(input.threadKey());
        if (currentProjection == null) {
            return;
        }
        MemoryThreadEvent enrichmentEvent =
                eventNormalizer
                        .normalizeEnrichment(
                                input, itemBackedEventsByKey, itemBackedEventTimeByItemId)
                        .orElse(null);
        if (enrichmentEvent == null) {
            return;
        }

        List<MemoryThreadEvent> eventRows =
                eventsByThreadKey.computeIfAbsent(input.threadKey(), ignored -> new ArrayList<>());
        long nextEventSeq =
                eventRows.stream().mapToLong(MemoryThreadEvent::eventSeq).max().orElse(0L) + 1L;
        eventRows.add(withEventSeq(enrichmentEvent, nextEventSeq));
        List<MemoryThreadMembership> membershipRows =
                membershipsByThreadKey.getOrDefault(input.threadKey(), List.of());
        projectionsByKey.put(
                input.threadKey(),
                structuralReducer.reduce(
                        currentProjection, List.copyOf(eventRows), List.copyOf(membershipRows), reductionNow));
    }

    private void accumulateCreationSupport(
            List<ThreadIntakeSignal> signals, CreationSupportState creationSupport) {
        for (List<CanonicalizedSignal> canonicalSignals :
                canonicalSignalsByType(signals).values()) {
            if (canonicalSignals.size() != 1) {
                continue;
            }
            CanonicalizedSignal canonicalSignal = canonicalSignals.getFirst();
            String threadKey = canonicalSignal.anchor().threadKey();
            creationSupport.supportCountByThreadKey().merge(threadKey, 1, Integer::sum);
            creationSupport
                    .createScoreByThreadKey()
                    .merge(
                            threadKey,
                            policy.createScore(
                                    canonicalSignal.signal().threadType(),
                                    canonicalSignal.signal().eligibility()),
                            Math::max);
            creationSupport
                    .latestSupportingItemIdByThreadKey()
                    .merge(threadKey, canonicalSignal.signal().triggerItemId(), Math::max);
            if (canonicalSignal.signal().threadType() == MemoryThreadType.WORK
                    || canonicalSignal.signal().threadType() == MemoryThreadType.CASE) {
                if (hasBoundMeaningfulMarker(canonicalSignal.signal())) {
                    creationSupport.markerEligibleThreadKeys().add(threadKey);
                }
            } else {
                creationSupport.markerEligibleThreadKeys().add(threadKey);
            }
        }
    }

    private Map<MemoryThreadType, List<CanonicalizedSignal>> canonicalSignalsByType(
            List<ThreadIntakeSignal> signals) {
        Map<MemoryThreadType, Map<String, CanonicalizedSignal>> byType = new LinkedHashMap<>();
        for (ThreadIntakeSignal signal : signals) {
            canonicalizer
                    .canonicalize(signal)
                    .ifPresent(
                            anchor ->
                                    byType.computeIfAbsent(
                                                    signal.threadType(),
                                                    ignored -> new LinkedHashMap<>())
                                            .merge(
                                                    anchor.threadKey(),
                                                    new CanonicalizedSignal(signal, anchor),
                                                    this::preferSignal));
        }
        Map<MemoryThreadType, List<CanonicalizedSignal>> result = new LinkedHashMap<>();
        byType.forEach(
                (threadType, canonicalSignals) ->
                        result.put(threadType, List.copyOf(canonicalSignals.values())));
        return result;
    }

    private CanonicalizedSignal preferSignal(
            CanonicalizedSignal current, CanonicalizedSignal candidate) {
        double currentScore =
                policy.createScore(current.signal().threadType(), current.signal().eligibility());
        double candidateScore =
                policy.createScore(
                        candidate.signal().threadType(), candidate.signal().eligibility());
        if (candidateScore != currentScore) {
            return candidateScore > currentScore ? candidate : current;
        }
        return candidate.signal().confidence() > current.signal().confidence()
                ? candidate
                : current;
    }

    private static Map<Long, Set<String>> durableEntityKeysByItemId(
            Map<Long, List<ItemEntityMention>> mentionsByItemId) {
        Map<Long, Set<String>> durableEntities = new LinkedHashMap<>();
        mentionsByItemId.forEach(
                (itemId, mentions) -> {
                    Set<String> entities =
                            mentions.stream()
                                    .map(ItemEntityMention::entityKey)
                                    .map(ThreadAnchorCanonicalizer::normalizeToken)
                                    .filter(Objects::nonNull)
                                    .filter(ThreadProjectionMaterializer::isDurableEntity)
                                    .collect(Collectors.toCollection(LinkedHashSet::new));
                    durableEntities.put(itemId, Set.copyOf(entities));
                });
        return durableEntities;
    }

    private static boolean isDurableEntity(String entityKey) {
        return entityKey.startsWith("special:")
                || entityKey.startsWith("person:")
                || entityKey.startsWith("organization:")
                || entityKey.startsWith("concept:");
    }

    private static MemoryThreadType threadTypeForMembership(
            MemoryThreadMembership membership,
            Map<String, MemoryThreadProjection> projectionsByKey) {
        MemoryThreadProjection projection = projectionsByKey.get(membership.threadKey());
        return projection == null ? null : projection.threadType();
    }

    private static Long otherItemId(long triggerItemId, ItemLink link) {
        if (link.sourceItemId() == triggerItemId) {
            return link.targetItemId();
        }
        if (link.targetItemId() == triggerItemId) {
            return link.sourceItemId();
        }
        return null;
    }

    private static boolean hasBoundMeaningfulMarker(ThreadIntakeSignal signal) {
        return signal.semanticMarkers().stream()
                .filter(marker -> marker.objectRef() != null && !marker.objectRef().isBlank())
                .anyMatch(ThreadProjectionMaterializer::isMeaningfulMarker);
    }

    private static boolean isMeaningfulMarker(ThreadIntakeSignal.SemanticMarker marker) {
        return switch (marker.eventType()) {
            case STATE_CHANGE,
                    BLOCKER_ADDED,
                    BLOCKER_CLEARED,
                    DECISION_MADE,
                    MILESTONE_REACHED,
                    RESOLUTION_DECLARED,
                    SETBACK ->
                    true;
            default -> false;
        };
    }

    private Map<Long, List<ItemLink>> adjacentLinksByItemId(MemoryId memoryId, Set<Long> itemIds) {
        Map<Long, List<ItemLink>> linksByItemId = new LinkedHashMap<>();
        for (ItemLink link : graphOperations.listItemLinks(memoryId)) {
            if (itemIds.contains(link.sourceItemId())) {
                linksByItemId
                        .computeIfAbsent(link.sourceItemId(), ignored -> new ArrayList<>())
                        .add(link);
            }
            if (itemIds.contains(link.targetItemId())) {
                linksByItemId
                        .computeIfAbsent(link.targetItemId(), ignored -> new ArrayList<>())
                        .add(link);
            }
        }
        return linksByItemId;
    }

    private static MemoryThreadProjection seedProjection(
            ThreadDecision decision, MemoryItem item, Instant eventTime) {
        Instant timestamp = Objects.requireNonNull(eventTime, "eventTime");
        String displayLabel =
                decision.anchorKey() != null && !decision.anchorKey().isBlank()
                        ? decision.anchorKey()
                        : decision.threadKey();
        return new MemoryThreadProjection(
                item.memoryId(),
                decision.threadKey(),
                decision.threadType(),
                decision.anchorKind(),
                decision.anchorKey(),
                displayLabel,
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.UNCERTAIN,
                item.content(),
                Map.of(),
                1,
                timestamp,
                null,
                null,
                null,
                0L,
                0L,
                timestamp,
                timestamp);
    }

    private static MemoryThreadMembership membership(
            ThreadIntakeSignal signal,
            ThreadDecision decision,
            long itemId,
            MemoryThreadMembershipRole role,
            boolean primary,
            double relevanceWeight,
            ThreadAdmissionEvidence evidence) {
        MemoryThreadMembershipRole admittedRole = admittedRole(role);
        return new MemoryThreadMembership(
                signal.memoryId(),
                decision.threadKey(),
                itemId,
                admittedRole,
                primary,
                normalizedRelevanceWeight(decision, admittedRole, relevanceWeight, evidence),
                signal.eventTime(),
                signal.eventTime());
    }

    private static MemoryThreadMembershipRole admittedRole(MemoryThreadMembershipRole role) {
        return switch (Objects.requireNonNull(role, "role")) {
            case TRIGGER, CORE -> role;
            default ->
                    throw new IllegalArgumentException(
                            "Milestone 1 only permits TRIGGER/CORE memberships");
        };
    }

    private static double normalizedRelevanceWeight(
            ThreadDecision decision,
            MemoryThreadMembershipRole role,
            double relevanceWeight,
            ThreadAdmissionEvidence evidence) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(evidence, "evidence");
        if (role == MemoryThreadMembershipRole.CORE
                || decision.action() == ThreadDecision.Action.CREATE
                || evidence.hasFamily(ThreadAdmissionEvidence.EXACT_ANCHOR)) {
            return 1.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, relevanceWeight));
    }

    private static boolean hasMembershipForThreadType(
            long itemId,
            MemoryThreadType threadType,
            Map<Long, List<MemoryThreadMembership>> membershipsByItemId,
            Map<String, MemoryThreadProjection> projectionsByKey) {
        return membershipsByItemId.getOrDefault(itemId, List.of()).stream()
                .anyMatch(
                        membership ->
                                threadTypeForMembership(membership, projectionsByKey)
                                        == threadType);
    }

    private static MemoryThreadEvent withEventSeq(MemoryThreadEvent event, long eventSeq) {
        return new MemoryThreadEvent(
                event.memoryId(),
                event.threadKey(),
                event.eventKey(),
                eventSeq,
                event.eventType(),
                event.eventTime(),
                event.eventPayloadJson(),
                event.eventPayloadVersion(),
                event.meaningful(),
                event.confidence(),
                event.createdAt());
    }

    private static void indexItemBackedEvent(
            MemoryThreadEvent event,
            Map<String, MemoryThreadEvent> itemBackedEventsByKey,
            Map<Long, Instant> itemBackedEventTimeByItemId) {
        if (!isItemBacked(event)) {
            return;
        }
        itemBackedEventsByKey.put(event.eventKey(), event);
        for (Long supportingItemId : supportingItemIds(event)) {
            itemBackedEventTimeByItemId.merge(
                    supportingItemId,
                    event.eventTime(),
                    (left, right) -> right.isAfter(left) ? right : left);
        }
    }

    private static boolean isItemBacked(MemoryThreadEvent event) {
        Object sources = event.eventPayloadJson().get("sources");
        if (!(sources instanceof List<?> sourceList)) {
            return false;
        }
        return sourceList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(map -> map.get("sourceType"))
                .map(String::valueOf)
                .anyMatch("ITEM"::equals);
    }

    private static List<Long> supportingItemIds(MemoryThreadEvent event) {
        Object sources = event.eventPayloadJson().get("sources");
        if (!(sources instanceof List<?> sourceList)) {
            return List.of();
        }
        List<Long> itemIds = new ArrayList<>();
        for (Object source : sourceList) {
            if (!(source instanceof Map<?, ?> sourceMap)) {
                continue;
            }
            if (!"ITEM".equals(String.valueOf(sourceMap.get("sourceType")))) {
                continue;
            }
            Object itemId = sourceMap.get("itemId");
            if (itemId instanceof Number number) {
                itemIds.add(number.longValue());
            }
        }
        return List.copyOf(itemIds);
    }

    public record MaterializedProjection(
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            Long lastProcessedItemId) {

        static MaterializedProjection empty() {
            return new MaterializedProjection(List.of(), List.of(), List.of(), null);
        }
    }
}
