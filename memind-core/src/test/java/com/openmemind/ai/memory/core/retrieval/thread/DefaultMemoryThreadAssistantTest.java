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
package com.openmemind.ai.memory.core.retrieval.thread;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DefaultMemoryThreadAssistantTest {

    @Test
    void assistantStaysDirectOnlyEvenWhenProjectionDataExists() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(101L, "Seed item"),
                                item(102L, "Another direct item"),
                                item(103L, "Third direct item"),
                                item(201L, "Thread member one"),
                                item(202L, "Thread member two")));
        store.threadOperations()
                .replaceProjection(
                        memoryId,
                        List.of(projection(memoryId, "topic:conversation:recovery")),
                        List.of(),
                        List.of(
                                membership(memoryId, "topic:conversation:recovery", 101L),
                                membership(memoryId, "topic:conversation:recovery", 201L),
                                membership(memoryId, "topic:conversation:recovery", 202L)),
                        null,
                        Instant.parse("2026-04-18T00:00:00Z"));

        DefaultMemoryThreadAssistant assistant = new DefaultMemoryThreadAssistant(store);
        List<ScoredResult> directWindow =
                List.of(scored("101", 0.92d), scored("102", 0.88d), scored("103", 0.80d));

        StepVerifier.create(
                        assistant.assist(
                                context(),
                                RetrievalConfig.simple(),
                                new TestThreadSettings(true, 1, 2, 1, Duration.ofMillis(100)),
                                directWindow))
                .assertNext(
                        result -> {
                            assertThat(result.items()).hasSize(3);
                            assertThat(result.items())
                                    .extracting(ScoredResult::sourceId)
                                    .containsExactly("101", "102", "103");
                            assertThat(result.stats().seedThreadCount()).isZero();
                            assertThat(result.stats().admittedMemberCount()).isZero();
                        })
                .verifyComplete();
    }

    private static QueryContext context() {
        return new QueryContext(
                TestMemoryIds.userAgent(),
                "what happened after that conversation",
                null,
                List.of(),
                Map.of(),
                null,
                null);
    }

    private static ScoredResult scored(String sourceId, double score) {
        return new ScoredResult(
                ScoredResult.SourceType.ITEM, sourceId, "item-" + sourceId, 0.8f, score);
    }

    private static MemoryThreadProjection projection(MemoryId memoryId, String threadKey) {
        return new MemoryThreadProjection(
                memoryId.toIdentifier(),
                threadKey,
                MemoryThreadType.TOPIC,
                "topic",
                "conversation:recovery",
                "Recovery Thread",
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.ONGOING,
                "Seed summary",
                Map.of(),
                1,
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                null,
                1,
                3,
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"));
    }

    private static MemoryThreadMembership membership(
            MemoryId memoryId, String threadKey, long itemId) {
        return new MemoryThreadMembership(
                memoryId.toIdentifier(),
                threadKey,
                itemId,
                MemoryThreadMembershipRole.CORE,
                itemId == 101L,
                0.95d,
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"));
    }

    private static MemoryItem item(Long id, String content) {
        return new MemoryItem(
                id,
                TestMemoryIds.userAgent().toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
                "conversation",
                "vec-" + id,
                "raw-" + id,
                "hash-" + id,
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                Map.of(),
                Instant.parse("2026-04-18T00:00:00Z"),
                MemoryItemType.FACT);
    }

    private record TestThreadSettings(
            boolean enabled,
            int maxThreads,
            int maxMembersPerThread,
            int protectDirectTopK,
            Duration timeout)
            implements RetrievalMemoryThreadSettings {}
}
