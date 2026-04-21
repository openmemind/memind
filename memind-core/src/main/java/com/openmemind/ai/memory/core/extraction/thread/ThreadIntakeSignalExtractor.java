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
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure signal extraction from committed item and graph evidence.
 */
public final class ThreadIntakeSignalExtractor {

    private final ThreadMaterializationPolicy policy;

    public ThreadIntakeSignalExtractor(ThreadMaterializationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public List<ThreadIntakeSignal> extract(
            MemoryItem triggerItem,
            List<ItemEntityMention> mentions,
            List<ItemLink> adjacentLinks,
            List<EntityCooccurrence> cooccurrences) {
        Objects.requireNonNull(triggerItem, "triggerItem");
        List<ItemEntityMention> mentionRows = mentions == null ? List.of() : List.copyOf(mentions);
        List<ItemLink> linkRows = adjacentLinks == null ? List.of() : List.copyOf(adjacentLinks);
        List<EntityCooccurrence> cooccurrenceRows =
                cooccurrences == null ? List.of() : List.copyOf(cooccurrences);

        Set<String> entityKeys = new LinkedHashSet<>();
        for (ItemEntityMention mention : mentionRows) {
            if (Objects.equals(triggerItem.id(), mention.itemId())) {
                entityKeys.add(mention.entityKey());
            }
        }

        Instant semanticTime = semanticTime(triggerItem);
        ArrayList<ThreadIntakeSignal> signals = new ArrayList<>();

        List<String> relationshipParticipants =
                entityKeys.stream()
                        .filter(ThreadIntakeSignalExtractor::isRelationshipParticipant)
                        .sorted()
                        .toList();
        if (validRelationshipPair(relationshipParticipants)) {
            signals.add(
                    new ThreadIntakeSignal(
                            triggerItem.memoryId(),
                            triggerItem.id(),
                            triggerItem.content(),
                            semanticTime,
                            MemoryThreadType.RELATIONSHIP,
                            List.of(
                                    new ThreadIntakeSignal.AnchorCandidate(
                                            "relationship", null, relationshipParticipants, 1.0d)),
                            new ThreadIntakeSignal.ThreadEligibilityScore(
                                    1.0d,
                                    relationshipContinuity(
                                            relationshipParticipants, linkRows, cooccurrenceRows),
                                    statefulness(triggerItem)),
                            List.of(triggerItem.id()),
                            List.of(),
                            List.of(),
                            0.95d));
        }

        entityKeys.stream()
                .filter(entityKey -> entityKey.startsWith("concept:"))
                .sorted()
                .findFirst()
                .ifPresent(
                        conceptKey ->
                                signals.add(
                                        new ThreadIntakeSignal(
                                                triggerItem.memoryId(),
                                                triggerItem.id(),
                                                triggerItem.content(),
                                                semanticTime,
                                                MemoryThreadType.TOPIC,
                                                List.of(
                                                        new ThreadIntakeSignal.AnchorCandidate(
                                                                "topic",
                                                                conceptKey,
                                                                List.of(),
                                                                0.80d)),
                                                new ThreadIntakeSignal.ThreadEligibilityScore(
                                                        0.72d,
                                                        topicContinuity(linkRows, cooccurrenceRows),
                                                        statefulness(triggerItem)),
                                                List.of(triggerItem.id()),
                                                List.of(),
                                                List.of(),
                                                0.88d)));

        return List.copyOf(signals);
    }

    private double relationshipContinuity(
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

    private double topicContinuity(
            List<ItemLink> adjacentLinks, List<EntityCooccurrence> cooccurrences) {
        double linkStrength =
                adjacentLinks.stream()
                        .map(ItemLink::strength)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(0.0d);
        int cooccurrenceStrength =
                cooccurrences.stream()
                        .mapToInt(EntityCooccurrence::cooccurrenceCount)
                        .max()
                        .orElse(0);
        return Math.max(0.75d, Math.max(linkStrength, Math.min(1.0d, cooccurrenceStrength / 4.0d)));
    }

    private static double statefulness(MemoryItem item) {
        return item.category() == MemoryCategory.EVENT ? 0.90d : 0.20d;
    }

    private static boolean isRelationshipParticipant(String entityKey) {
        return entityKey != null
                && (entityKey.startsWith("person:") || entityKey.startsWith("special:"));
    }

    private static boolean validRelationshipPair(List<String> participants) {
        if (participants.size() != 2) {
            return false;
        }
        long personCount =
                participants.stream().filter(token -> token.startsWith("person:")).count();
        long specialCount =
                participants.stream().filter(token -> token.startsWith("special:")).count();
        return personCount >= 1 && specialCount < 2;
    }

    private static Instant semanticTime(MemoryItem item) {
        if (item.occurredAt() != null) {
            return item.occurredAt();
        }
        if (item.occurredStart() != null) {
            return item.occurredStart();
        }
        if (item.observedAt() != null) {
            return item.observedAt();
        }
        if (item.createdAt() != null) {
            return item.createdAt();
        }
        return Instant.EPOCH;
    }
}
