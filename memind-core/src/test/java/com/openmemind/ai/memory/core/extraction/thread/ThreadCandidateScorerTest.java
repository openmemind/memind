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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ThreadCandidateScorerTest {

    @Test
    void explicitContinuityOutranksCausalTemporalSemanticAndEntitySupport() {
        ThreadCandidateScorer scorer = new ThreadCandidateScorer(ThreadMaterializationPolicy.v1());
        MemoryItem item = item(900L, "The user is still working through travel logistics.");
        List<ThreadCandidate> candidates =
                List.of(
                        candidate("topic:topic:concept:travel", Set.of(810L), false, true),
                        candidate("topic:topic:concept:japan", Set.of(811L), false, false),
                        candidate("topic:topic:concept:visa", Set.of(812L), false, false),
                        candidate("topic:topic:concept:packing", Set.of(813L), false, false),
                        candidate("topic:topic:concept:passport", Set.of(814L), false, false));
        List<ItemLink> adjacentLinks =
                List.of(
                        causalLink(item.id(), 811L, 1.0d),
                        temporalLink(item.id(), 812L, 1.0d),
                        semanticLink(item.id(), 813L, 1.0d));
        Map<Long, Set<String>> durableEntityKeysByItemId = new LinkedHashMap<>();
        durableEntityKeysByItemId.put(item.id(), Set.of("concept:travel"));
        durableEntityKeysByItemId.put(814L, Set.of("concept:travel"));

        List<ThreadCandidateScore> scored =
                scorer.score(item, candidates, adjacentLinks, durableEntityKeysByItemId);

        assertThat(scored)
                .extracting(score -> score.candidate().thread().threadKey())
                .startsWith("topic:topic:concept:travel");
    }

    @Test
    void semanticOnlyCandidateStaysBelowDefaultMatchThreshold() {
        ThreadMaterializationPolicy policy = ThreadMaterializationPolicy.v1();
        ThreadCandidateScorer scorer = new ThreadCandidateScorer(policy);
        MemoryItem item = item(901L, "The passport checklist feels related.");
        List<ThreadCandidate> candidates =
                List.of(candidate("topic:topic:concept:passport", Set.of(820L), false, false));
        List<ItemLink> adjacentLinks = List.of(semanticLink(item.id(), 820L, 1.0d));

        List<ThreadCandidateScore> scored = scorer.score(item, candidates, adjacentLinks, Map.of());

        assertThat(scored)
                .singleElement()
                .satisfies(
                        score -> {
                            assertThat(score.finalScore()).isLessThan(policy.matchThreshold());
                            assertThat(score.finalScore()).isEqualTo(0.75d);
                        });
    }

    private static ThreadCandidate candidate(
            String threadKey,
            Set<Long> memberItemIds,
            boolean exactAnchorMatch,
            boolean explicitContinuityMatch) {
        return new ThreadCandidate(
                projection(threadKey, memberItemIds.size()),
                memberItemIds,
                exactAnchorMatch,
                explicitContinuityMatch);
    }

    private static MemoryThreadProjection projection(String threadKey, long memberCount) {
        String anchorKey = threadKey.substring(threadKey.lastIndexOf(':') + 1);
        return new MemoryThreadProjection(
                "memory-user-agent",
                threadKey,
                MemoryThreadType.TOPIC,
                "topic",
                anchorKey,
                anchorKey,
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.ONGOING,
                anchorKey,
                Map.of(),
                1,
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"),
                null,
                2,
                memberCount,
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"));
    }

    private static MemoryItem item(long itemId, String content) {
        return new MemoryItem(
                itemId,
                "memory-user-agent",
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vec-" + itemId,
                "raw-" + itemId,
                "hash-" + itemId,
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"),
                Map.of(),
                Instant.parse("2026-04-20T09:00:00Z"),
                MemoryItemType.FACT);
    }

    private static ItemLink causalLink(long sourceItemId, long targetItemId, double strength) {
        return new ItemLink(
                "memory-user-agent",
                sourceItemId,
                targetItemId,
                ItemLinkType.CAUSAL,
                "caused_by",
                null,
                strength,
                Map.of("relationType", "caused_by"),
                Instant.parse("2026-04-20T09:00:00Z"));
    }

    private static ItemLink temporalLink(long sourceItemId, long targetItemId, double strength) {
        return new ItemLink(
                "memory-user-agent",
                sourceItemId,
                targetItemId,
                ItemLinkType.TEMPORAL,
                "nearby",
                null,
                strength,
                Map.of("relationType", "nearby"),
                Instant.parse("2026-04-20T09:00:00Z"));
    }

    private static ItemLink semanticLink(long sourceItemId, long targetItemId, double strength) {
        return new ItemLink(
                "memory-user-agent",
                sourceItemId,
                targetItemId,
                ItemLinkType.SEMANTIC,
                null,
                "vector_search",
                strength,
                Map.of("source", "vector_search"),
                Instant.parse("2026-04-20T09:00:00Z"));
    }
}
