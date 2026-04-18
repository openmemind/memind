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
import com.openmemind.ai.memory.core.data.MemoryThread;
import com.openmemind.ai.memory.core.data.MemoryThreadItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadStatus;
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
    void assistantSeedsFromDirectHitsAndKeepsWindowBounded() {
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
        store.threadOperations().upsertThreads(memoryId, List.of(thread(memoryId, 501L)));
        store.threadOperations()
                .upsertThreadItems(
                        memoryId,
                        List.of(
                                membership(memoryId, 601L, 501L, 101L, 1),
                                membership(memoryId, 602L, 501L, 201L, 2),
                                membership(memoryId, 603L, 501L, 202L, 3)));

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
                                    .containsExactly("101", "201", "202");
                            assertThat(result.stats().seedThreadCount()).isEqualTo(1);
                            assertThat(result.stats().admittedMemberCount()).isEqualTo(2);
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

    private static MemoryThread thread(MemoryId memoryId, long threadId) {
        return new MemoryThread(
                threadId,
                memoryId.toIdentifier(),
                "ep:101",
                "recovery",
                "Recovery Thread",
                "Seed summary",
                MemoryThreadStatus.OPEN,
                0.90d,
                Instant.parse("2026-04-18T00:00:00Z"),
                null,
                Instant.parse("2026-04-18T00:00:00Z"),
                101L,
                101L,
                1,
                Map.of(),
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                false);
    }

    private static MemoryThreadItem membership(
            MemoryId memoryId, long id, long threadId, long itemId, int sequenceHint) {
        return new MemoryThreadItem(
                id,
                memoryId.toIdentifier(),
                threadId,
                itemId,
                0.95d,
                MemoryThreadRole.CORE,
                sequenceHint,
                Instant.parse("2026-04-18T00:00:00Z"),
                Map.of(),
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                false);
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
