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
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Scores and deterministically orders discovered thread candidates.
 */
final class ThreadCandidateScorer {

    private static final double EXACT_ANCHOR_SCORE = 1.0d;
    private static final double EXPLICIT_CONTINUITY_SCORE = 0.95d;
    private static final double TEMPORAL_MULTIPLIER = 0.85d;
    private static final double SEMANTIC_MULTIPLIER = 0.75d;
    private static final double ENTITY_MULTIPLIER = 0.55d;
    private static final double SECONDARY_NON_ENTITY_BONUS = 0.10d;
    private static final double ENTITY_SUPPORT_BONUS = 0.05d;
    private static final double MIN_NON_ENTITY_SUPPORT = 0.35d;

    private final ThreadMaterializationPolicy policy;

    ThreadCandidateScorer(ThreadMaterializationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    List<ThreadCandidateScore> score(
            MemoryItem item,
            List<ThreadCandidate> candidates,
            List<ItemLink> adjacentLinks,
            Map<Long, Set<String>> durableEntityKeysByItemId) {
        Objects.requireNonNull(item, "item");
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Set<String> triggerEntities = durableEntityKeysByItemId.getOrDefault(item.id(), Set.of());
        ArrayList<ThreadCandidateScore> exactAnchorMatches = new ArrayList<>();
        ArrayList<ThreadCandidateScore> discoveredCandidates = new ArrayList<>();
        for (ThreadCandidate candidate : candidates) {
            ThreadCandidateScore score =
                    scoreCandidate(
                            candidate,
                            adjacentLinks == null ? List.of() : adjacentLinks,
                            durableEntityKeysByItemId,
                            triggerEntities);
            if (score.isExactAnchorMatch()) {
                exactAnchorMatches.add(score);
            } else {
                discoveredCandidates.add(score);
            }
        }

        exactAnchorMatches.sort(
                Comparator.comparing(
                                (ThreadCandidateScore score) -> score.candidate().thread().lastEventAt(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(score -> score.candidate().thread().threadKey()));
        discoveredCandidates.sort(resolutionOrder());

        ArrayList<ThreadCandidateScore> retained = new ArrayList<>(exactAnchorMatches);
        retained.addAll(
                discoveredCandidates.subList(
                        0, Math.min(policy.maxCandidateThreads(), discoveredCandidates.size())));
        return List.copyOf(retained);
    }

    private ThreadCandidateScore scoreCandidate(
            ThreadCandidate candidate,
            List<ItemLink> adjacentLinks,
            Map<Long, Set<String>> durableEntityKeysByItemId,
            Set<String> triggerEntities) {
        double exactAnchorScore = candidate.exactAnchorMatch() ? EXACT_ANCHOR_SCORE : 0.0d;
        double explicitContinuityScore =
                candidate.explicitContinuityMatch() ? EXPLICIT_CONTINUITY_SCORE : 0.0d;
        double causalScore =
                strongestLinkStrength(adjacentLinks, candidate.memberItemIds(), ItemLinkType.CAUSAL);
        double temporalScore =
                strongestLinkStrength(adjacentLinks, candidate.memberItemIds(), ItemLinkType.TEMPORAL);
        double semanticScore =
                strongestLinkStrength(adjacentLinks, candidate.memberItemIds(), ItemLinkType.SEMANTIC);
        double entityScore =
                entitySupportScore(
                        triggerEntities, candidate.memberItemIds(), durableEntityKeysByItemId);
        return new ThreadCandidateScore(
                candidate,
                exactAnchorScore,
                explicitContinuityScore,
                causalScore,
                temporalScore,
                semanticScore,
                entityScore,
                compositeScore(
                        exactAnchorScore,
                        explicitContinuityScore,
                        causalScore,
                        temporalScore,
                        semanticScore,
                        entityScore),
                dominantFamilyRank(
                        exactAnchorScore,
                        explicitContinuityScore,
                        causalScore,
                        temporalScore,
                        semanticScore),
                dominantFamilyScore(
                        exactAnchorScore,
                        explicitContinuityScore,
                        causalScore,
                        temporalScore,
                        semanticScore,
                        entityScore));
    }

    private static Comparator<ThreadCandidateScore> resolutionOrder() {
        return Comparator.comparingInt(ThreadCandidateScore::dominantFamilyRank)
                .thenComparing(
                        ThreadCandidateScore::dominantFamilyScore, Comparator.reverseOrder())
                .thenComparing(ThreadCandidateScore::finalScore, Comparator.reverseOrder())
                .thenComparing(
                        score -> score.candidate().thread().lastEventAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(score -> score.candidate().thread().threadKey());
    }

    private static double strongestLinkStrength(
            List<ItemLink> adjacentLinks, Set<Long> memberItemIds, ItemLinkType linkType) {
        return adjacentLinks.stream()
                .filter(link -> link.linkType() == linkType)
                .filter(
                        link ->
                                memberItemIds.contains(link.sourceItemId())
                                        || memberItemIds.contains(link.targetItemId()))
                .map(ItemLink::strength)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0d);
    }

    private static double entitySupportScore(
            Set<String> triggerEntities,
            Set<Long> memberItemIds,
            Map<Long, Set<String>> durableEntityKeysByItemId) {
        if (triggerEntities.isEmpty()) {
            return 0.0d;
        }
        double strongest = 0.0d;
        for (Long memberItemId : memberItemIds) {
            Set<String> memberEntities =
                    durableEntityKeysByItemId.getOrDefault(memberItemId, Set.of());
            if (memberEntities.isEmpty()) {
                continue;
            }
            long overlap = triggerEntities.stream().filter(memberEntities::contains).count();
            double score =
                    overlap == 0L
                            ? 0.0d
                            : overlap
                                    / (double)
                                            Math.max(triggerEntities.size(), memberEntities.size());
            strongest = Math.max(strongest, score);
        }
        return strongest;
    }

    private static double compositeScore(
            double exactAnchorScore,
            double explicitContinuityScore,
            double causalScore,
            double temporalScore,
            double semanticScore,
            double entityScore) {
        if (exactAnchorScore == EXACT_ANCHOR_SCORE) {
            return EXACT_ANCHOR_SCORE;
        }
        double baseScore =
                Math.max(
                        explicitContinuityScore,
                        Math.max(
                                causalScore,
                                Math.max(
                                        temporalScore * TEMPORAL_MULTIPLIER,
                                        Math.max(
                                                semanticScore * SEMANTIC_MULTIPLIER,
                                                entityScore * ENTITY_MULTIPLIER))));
        int strongNonEntityFamilies = 0;
        if (explicitContinuityScore >= MIN_NON_ENTITY_SUPPORT) {
            strongNonEntityFamilies++;
        }
        if (causalScore >= MIN_NON_ENTITY_SUPPORT) {
            strongNonEntityFamilies++;
        }
        if (temporalScore >= MIN_NON_ENTITY_SUPPORT) {
            strongNonEntityFamilies++;
        }
        if (semanticScore >= MIN_NON_ENTITY_SUPPORT) {
            strongNonEntityFamilies++;
        }
        double score = baseScore;
        if (strongNonEntityFamilies >= 2) {
            score += SECONDARY_NON_ENTITY_BONUS;
        }
        if (entityScore >= 0.40d && strongNonEntityFamilies >= 1) {
            score += ENTITY_SUPPORT_BONUS;
        }
        return Math.max(0.0d, Math.min(1.0d, score));
    }

    private static int dominantFamilyRank(
            double exactAnchorScore,
            double explicitContinuityScore,
            double causalScore,
            double temporalScore,
            double semanticScore) {
        if (exactAnchorScore > 0.0d) {
            return -1;
        }
        if (explicitContinuityScore > 0.0d) {
            return 0;
        }
        if (causalScore > 0.0d) {
            return 1;
        }
        if (temporalScore > 0.0d) {
            return 2;
        }
        if (semanticScore > 0.0d) {
            return 3;
        }
        return 4;
    }

    private static double dominantFamilyScore(
            double exactAnchorScore,
            double explicitContinuityScore,
            double causalScore,
            double temporalScore,
            double semanticScore,
            double entityScore) {
        if (exactAnchorScore > 0.0d) {
            return exactAnchorScore;
        }
        if (explicitContinuityScore > 0.0d) {
            return explicitContinuityScore;
        }
        if (causalScore > 0.0d) {
            return causalScore;
        }
        if (temporalScore > 0.0d) {
            return temporalScore;
        }
        if (semanticScore > 0.0d) {
            return semanticScore;
        }
        return entityScore;
    }
}
