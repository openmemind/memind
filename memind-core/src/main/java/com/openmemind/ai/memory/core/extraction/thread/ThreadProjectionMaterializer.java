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
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic materialization helper shared by the incremental worker and the full rebuilder.
 */
final class ThreadProjectionMaterializer {

    private final ItemOperations itemOperations;
    private final GraphOperations graphOperations;
    private final ThreadIntakeSignalExtractor signalExtractor;
    private final ThreadDecisionEngine decisionEngine;
    private final ThreadEventNormalizer eventNormalizer;
    private final ThreadStructuralReducer structuralReducer;

    ThreadProjectionMaterializer(
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            ThreadMaterializationPolicy policy) {
        this.itemOperations = Objects.requireNonNull(itemOperations, "itemOperations");
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
        Objects.requireNonNull(policy, "policy");
        this.signalExtractor = new ThreadIntakeSignalExtractor(policy);
        this.decisionEngine = new ThreadDecisionEngine(policy);
        this.eventNormalizer = new ThreadEventNormalizer();
        this.structuralReducer = new ThreadStructuralReducer(policy);
    }

    MaterializedProjection materializeUpTo(MemoryId memoryId, long cutoffItemId) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (cutoffItemId <= 0L) {
            return MaterializedProjection.empty();
        }

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

        Map<String, MemoryThreadProjection> projectionsByKey = new LinkedHashMap<>();
        Map<String, List<MemoryThreadEvent>> eventsByThreadKey = new LinkedHashMap<>();
        Map<String, List<MemoryThreadMembership>> membershipsByThreadKey = new LinkedHashMap<>();

        for (MemoryItem item : items) {
            List<ThreadIntakeSignal> signals =
                    signalExtractor.extract(
                            item,
                            mentionsByItemId.getOrDefault(item.id(), List.of()),
                            adjacentLinksByItemId.getOrDefault(item.id(), List.of()),
                            List.of());
            for (ThreadIntakeSignal signal : signals) {
                ThreadDecision decision =
                        decisionEngine.decide(signal, List.copyOf(projectionsByKey.values()));
                if (decision.action() == ThreadDecision.Action.IGNORE) {
                    continue;
                }

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
                for (MemoryThreadEvent event : eventNormalizer.normalize(decision, signal)) {
                    eventRows.add(withEventSeq(event, nextEventSeq++));
                }
                if (membershipRows.stream()
                        .noneMatch(membership -> membership.itemId() == item.id())) {
                    membershipRows.add(
                            membership(signal, decision, item.id(), membershipRows.isEmpty()));
                }

                projectionsByKey.put(
                        decision.threadKey(),
                        structuralReducer.reduce(
                                baseProjection,
                                List.copyOf(eventRows),
                                List.copyOf(membershipRows)));
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
        Instant timestamp = eventTime != null ? eventTime : Instant.EPOCH;
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
            ThreadIntakeSignal signal, ThreadDecision decision, long itemId, boolean primary) {
        return new MemoryThreadMembership(
                signal.memoryId(),
                decision.threadKey(),
                itemId,
                MemoryThreadMembershipRole.TRIGGER,
                primary,
                1.0d,
                signal.eventTime(),
                signal.eventTime());
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

    record MaterializedProjection(
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            Long lastProcessedItemId) {

        static MaterializedProjection empty() {
            return new MaterializedProjection(List.of(), List.of(), List.of(), null);
        }
    }
}
