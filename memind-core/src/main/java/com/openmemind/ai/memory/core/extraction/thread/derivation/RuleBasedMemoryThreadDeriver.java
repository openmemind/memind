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
package com.openmemind.ai.memory.core.extraction.thread.derivation;

import com.openmemind.ai.memory.core.builder.MemoryThreadDerivationOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryThread;
import com.openmemind.ai.memory.core.data.MemoryThreadItem;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadStatus;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.thread.MemoryThreadOperations;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic rule-based attach/create/no-attach derivation.
 */
public final class RuleBasedMemoryThreadDeriver {

    private final MemoryThreadOptions options;

    public RuleBasedMemoryThreadDeriver() {
        this(
                MemoryThreadOptions.defaults()
                        .withEnabled(true)
                        .withDerivation(
                                MemoryThreadDerivationOptions.defaults().withEnabled(true)));
    }

    public RuleBasedMemoryThreadDeriver(MemoryThreadOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public MemoryThreadDerivationOutcome derive(
            MemoryId memoryId,
            List<MemoryItem> incomingItems,
            MemoryThreadOperations threadOperations,
            MemoryStore store) {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(threadOperations, "threadOperations");
        Objects.requireNonNull(store, "store");
        if (!options.enabled() || !options.derivation().enabled() || incomingItems == null) {
            return MemoryThreadDerivationOutcome.empty();
        }

        List<MemoryItem> orderedIncoming = orderedItems(incomingItems);
        if (orderedIncoming.isEmpty()) {
            return MemoryThreadDerivationOutcome.empty();
        }

        Map<Long, MemoryThread> workingThreads =
                threadOperations.listThreads(memoryId).stream()
                        .filter(thread -> thread.id() != null && !thread.deleted())
                        .collect(
                                Collectors.toMap(
                                        MemoryThread::id,
                                        thread -> thread,
                                        (left, right) -> right,
                                        LinkedHashMap::new));
        Map<Long, MemoryThreadItem> workingMemberships =
                threadOperations.listThreadItems(memoryId).stream()
                        .filter(item -> item.itemId() != null && !item.deleted())
                        .collect(
                                Collectors.toMap(
                                        MemoryThreadItem::itemId,
                                        item -> item,
                                        (left, right) -> right,
                                        LinkedHashMap::new));
        Map<Long, Set<String>> entityKeysByItemId = entityKeysByItemId(memoryId, store);
        Map<Long, List<ItemLink>> adjacentLinksByItemId = adjacentLinksByItemId(memoryId, store);

        List<MemoryThread> createdThreads = new ArrayList<>();
        List<MemoryThreadItem> memberships = new ArrayList<>();
        for (MemoryItem item : orderedIncoming) {
            if (item.id() == null || workingMemberships.containsKey(item.id())) {
                continue;
            }

            ThreadCandidate candidate =
                    bestAttachCandidate(
                            item,
                            workingThreads.values(),
                            workingMemberships,
                            entityKeysByItemId,
                            adjacentLinksByItemId);
            if (candidate != null
                    && candidate.score() >= options.rule().matchThreshold()
                    && candidate.thread().id() != null) {
                MemoryThreadItem membership =
                        membershipForExistingThread(
                                memoryId,
                                candidate.thread().id(),
                                item,
                                candidate.score(),
                                nextSequence(candidate.thread().id(), workingMemberships.values()));
                workingMemberships.put(item.id(), membership);
                memberships.add(membership);
                continue;
            }

            double createScore =
                    newThreadScore(
                            item,
                            entityKeysByItemId.get(item.id()),
                            adjacentLinksByItemId.get(item.id()));
            if (createScore < options.rule().newThreadThreshold()) {
                continue;
            }

            MemoryThread newThread =
                    newThread(memoryId, item, createScore, entityKeysByItemId.get(item.id()));
            MemoryThreadItem membership =
                    membershipForNewThread(memoryId, item, newThread.id(), createScore);
            workingThreads.put(newThread.id(), newThread);
            workingMemberships.put(item.id(), membership);
            createdThreads.add(newThread);
            memberships.add(membership);
        }
        return new MemoryThreadDerivationOutcome(createdThreads, memberships);
    }

    private List<MemoryItem> orderedItems(List<MemoryItem> items) {
        return items.stream()
                .filter(Objects::nonNull)
                .sorted(
                        Comparator.comparing(
                                        RuleBasedMemoryThreadDeriver::activityAt,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(
                                        MemoryItem::id, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private Map<Long, Set<String>> entityKeysByItemId(MemoryId memoryId, MemoryStore store) {
        Map<Long, Set<String>> entityKeys = new HashMap<>();
        for (ItemEntityMention mention : store.graphOperations().listItemEntityMentions(memoryId)) {
            entityKeys
                    .computeIfAbsent(mention.itemId(), ignored -> new LinkedHashSet<>())
                    .add(mention.entityKey());
        }
        return entityKeys;
    }

    private Map<Long, List<ItemLink>> adjacentLinksByItemId(MemoryId memoryId, MemoryStore store) {
        Map<Long, List<ItemLink>> links = new HashMap<>();
        for (ItemLink link : store.graphOperations().listItemLinks(memoryId)) {
            links.computeIfAbsent(link.sourceItemId(), ignored -> new ArrayList<>()).add(link);
            links.computeIfAbsent(link.targetItemId(), ignored -> new ArrayList<>()).add(link);
        }
        return links;
    }

    private ThreadCandidate bestAttachCandidate(
            MemoryItem item,
            Collection<MemoryThread> threads,
            Map<Long, MemoryThreadItem> membershipsByItemId,
            Map<Long, Set<String>> entityKeysByItemId,
            Map<Long, List<ItemLink>> adjacentLinksByItemId) {
        List<MemoryThread> candidateThreads =
                threads.stream()
                        .filter(thread -> thread.status() != MemoryThreadStatus.CLOSED)
                        .sorted(
                                Comparator.comparing(
                                                MemoryThread::lastActivityAt,
                                                Comparator.nullsLast(Comparator.reverseOrder()))
                                        .thenComparing(
                                                MemoryThread::id,
                                                Comparator.nullsLast(Long::compareTo)))
                        .limit(options.rule().maxCandidateThreads())
                        .toList();
        ThreadCandidate best = null;
        for (MemoryThread thread : candidateThreads) {
            if (thread.id() == null) {
                continue;
            }
            Set<Long> memberIds = memberIds(thread.id(), membershipsByItemId.values());
            if (memberIds.isEmpty()) {
                continue;
            }
            double score =
                    attachScore(
                            item,
                            thread,
                            memberIds,
                            entityKeysByItemId,
                            adjacentLinksByItemId.get(item.id()));
            if (best == null || score > best.score()) {
                best = new ThreadCandidate(thread, score);
            }
        }
        return best;
    }

    private Set<Long> memberIds(Long threadId, Collection<MemoryThreadItem> memberships) {
        return memberships.stream()
                .filter(item -> threadId.equals(item.threadId()))
                .sorted(
                        Comparator.comparingInt(MemoryThreadItem::sequenceHint)
                                .reversed()
                                .thenComparing(MemoryThreadItem::itemId))
                .limit(options.rule().maxMembersPerThread())
                .map(MemoryThreadItem::itemId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private double attachScore(
            MemoryItem item,
            MemoryThread thread,
            Set<Long> memberIds,
            Map<Long, Set<String>> entityKeysByItemId,
            List<ItemLink> adjacentLinks) {
        double bridgeStrength = bridgeStrength(memberIds, adjacentLinks);
        boolean sharedEntities =
                intersects(entityKeysByItemId.get(item.id()), memberIds, entityKeysByItemId);
        boolean closeInTime = closeInTime(item, thread);
        double score = bridgeStrength * 0.65d;
        if (sharedEntities) {
            score += 0.25d;
        }
        if (closeInTime) {
            score += 0.10d;
        }
        return Math.min(1.0d, score);
    }

    private double bridgeStrength(Set<Long> memberIds, List<ItemLink> adjacentLinks) {
        if (adjacentLinks == null || adjacentLinks.isEmpty()) {
            return 0.0d;
        }
        double strongest = 0.0d;
        for (ItemLink link : adjacentLinks) {
            if (link.linkType() != ItemLinkType.TEMPORAL
                    && link.linkType() != ItemLinkType.CAUSAL) {
                continue;
            }
            long otherId =
                    memberIds.contains(link.sourceItemId())
                            ? link.sourceItemId()
                            : link.targetItemId();
            if (!memberIds.contains(otherId)) {
                continue;
            }
            strongest = Math.max(strongest, link.strength());
        }
        return strongest;
    }

    private boolean intersects(
            Set<String> incomingEntityKeys,
            Set<Long> memberIds,
            Map<Long, Set<String>> entityKeysByItemId) {
        if (incomingEntityKeys == null || incomingEntityKeys.isEmpty()) {
            return false;
        }
        for (Long memberId : memberIds) {
            Set<String> memberEntityKeys = entityKeysByItemId.get(memberId);
            if (memberEntityKeys == null || memberEntityKeys.isEmpty()) {
                continue;
            }
            for (String entityKey : incomingEntityKeys) {
                if (memberEntityKeys.contains(entityKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean closeInTime(MemoryItem item, MemoryThread thread) {
        Instant itemTime = activityAt(item);
        if (itemTime == null || thread.lastActivityAt() == null) {
            return false;
        }
        Duration gap = Duration.between(thread.lastActivityAt(), itemTime).abs();
        return gap.compareTo(Duration.ofDays(30)) <= 0;
    }

    private double newThreadScore(
            MemoryItem item, Set<String> entityKeys, List<ItemLink> adjacentLinks) {
        double score = 0.0d;
        if (entityKeys != null && !entityKeys.isEmpty()) {
            score += 0.45d;
        }
        if (adjacentLinks != null
                && adjacentLinks.stream()
                        .anyMatch(
                                link ->
                                        link.linkType() == ItemLinkType.TEMPORAL
                                                || link.linkType() == ItemLinkType.CAUSAL)) {
            score += 0.20d;
        }
        if (activityAt(item) != null) {
            score += 0.20d;
        }
        if (wordCount(item.content()) >= 5) {
            score += 0.20d;
        }
        return Math.min(1.0d, score);
    }

    private MemoryThread newThread(
            MemoryId memoryId, MemoryItem item, double score, Set<String> entityKeys) {
        Instant activityAt = activityAt(item);
        Instant createdAt = item.createdAt() != null ? item.createdAt() : activityAt;
        String episodeType = deriveEpisodeType(item, entityKeys);
        String title =
                item.content() == null || item.content().isBlank()
                        ? humanize(episodeType)
                        : firstWords(item.content(), 5);
        String summary =
                item.content() == null || item.content().isBlank() ? title : item.content();
        return new MemoryThread(
                item.id(),
                memoryId.toIdentifier(),
                "ep:" + item.id(),
                episodeType,
                title,
                summary,
                MemoryThreadStatus.OPEN,
                score,
                activityAt,
                null,
                activityAt,
                item.id(),
                item.id(),
                1,
                Map.of("derivedBy", "rule"),
                createdAt,
                createdAt,
                false);
    }

    private MemoryThreadItem membershipForExistingThread(
            MemoryId memoryId, Long threadId, MemoryItem item, double score, int sequenceHint) {
        Instant joinedAt = activityAt(item);
        Instant createdAt = item.createdAt() != null ? item.createdAt() : joinedAt;
        return new MemoryThreadItem(
                item.id(),
                memoryId.toIdentifier(),
                threadId,
                item.id(),
                score,
                MemoryThreadRole.CORE,
                sequenceHint,
                joinedAt,
                Map.of("derivedBy", "rule"),
                createdAt,
                createdAt,
                false);
    }

    private MemoryThreadItem membershipForNewThread(
            MemoryId memoryId, MemoryItem item, Long threadId, double score) {
        Instant joinedAt = activityAt(item);
        Instant createdAt = item.createdAt() != null ? item.createdAt() : joinedAt;
        return new MemoryThreadItem(
                item.id(),
                memoryId.toIdentifier(),
                threadId,
                item.id(),
                score,
                MemoryThreadRole.TRIGGER,
                1,
                joinedAt,
                Map.of("derivedBy", "rule"),
                createdAt,
                createdAt,
                false);
    }

    private int nextSequence(Long threadId, Collection<MemoryThreadItem> memberships) {
        return memberships.stream()
                        .filter(item -> threadId.equals(item.threadId()))
                        .mapToInt(MemoryThreadItem::sequenceHint)
                        .max()
                        .orElse(0)
                + 1;
    }

    private String deriveEpisodeType(MemoryItem item, Set<String> entityKeys) {
        if (entityKeys != null && !entityKeys.isEmpty()) {
            return humanize(entityKeys.iterator().next())
                    .replace(' ', '_')
                    .toLowerCase(Locale.ROOT);
        }
        return item.category().name().toLowerCase(Locale.ROOT) + "_thread";
    }

    private static Instant activityAt(MemoryItem item) {
        if (item.occurredAt() != null) {
            return item.occurredAt();
        }
        if (item.occurredStart() != null) {
            return item.occurredStart();
        }
        if (item.observedAt() != null) {
            return item.observedAt();
        }
        return item.createdAt();
    }

    private static int wordCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.trim().split("\\s+").length;
    }

    private static String firstWords(String content, int maxWords) {
        if (content == null || content.isBlank()) {
            return "Untitled";
        }
        String[] words = content.trim().split("\\s+");
        int count = Math.min(words.length, maxWords);
        return String.join(" ", java.util.Arrays.copyOf(words, count));
    }

    private static String humanize(String content) {
        if (content == null || content.isBlank()) {
            return "general";
        }
        return content.replace(':', ' ').replace('_', ' ').replace('-', ' ').trim();
    }

    private record ThreadCandidate(MemoryThread thread, double score) {}
}
