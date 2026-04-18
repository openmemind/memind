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
package com.openmemind.ai.memory.core.extraction.thread.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.builder.MemoryThreadDerivationOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryThreadItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.thread.derivation.RuleBasedMemoryThreadDeriver;
import com.openmemind.ai.memory.core.extraction.thread.text.RuleBasedMemoryThreadTextGenerator;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import com.openmemind.ai.memory.core.tracing.NoopMemoryObserver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemoryThreadBuildSchedulerTest {

    @Test
    void submitDerivationAndFlushCreateAndRefreshThread() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        MemoryItem item = item(101L, "Could not sleep for three nights after the breakup");
        store.itemOperations().insertItems(memoryId, List.of(item));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId, List.of(mention(memoryId, 101L, "emotion:breakup")));
        MemoryThreadBuildScheduler scheduler = scheduler(store, Optional.empty());

        scheduler.submitDerivation(memoryId, List.of(item), ItemExtractionConfig.defaults());
        scheduler.flush(memoryId);

        assertThat(store.threadOperations().listThreads(memoryId))
                .singleElement()
                .satisfies(
                        thread -> {
                            assertThat(thread.threadKey()).isEqualTo("ep:101");
                            assertThat(thread.title()).isNotBlank();
                            assertThat(thread.summarySnapshot()).isNotBlank();
                        });
        assertThat(store.threadOperations().listThreadItems(memoryId))
                .extracting(MemoryThreadItem::threadId, MemoryThreadItem::itemId)
                .containsExactly(tuple(101L, 101L));
        assertThat(scheduler.status().lastSuccessAt()).isNotNull();
        assertThat(scheduler.status().failureCount()).isZero();
    }

    @Test
    void forcedDisableReasonLeavesDerivationUnavailableAndSkipsQueuedWork() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        MemoryItem item = item(201L, "Could not sleep again");
        store.itemOperations().insertItems(memoryId, List.of(item));
        MemoryThreadBuildScheduler scheduler =
                scheduler(
                        store,
                        Optional.of(
                                "memoryThread.derivation.enabled requires extraction.item.graph"
                                        + " enabled"));

        scheduler.submitDerivation(memoryId, List.of(item), ItemExtractionConfig.defaults());
        scheduler.flush(memoryId);

        assertThat(store.threadOperations().listThreads(memoryId)).isEmpty();
        assertThat(scheduler.status().derivationAvailable()).isFalse();
        assertThat(scheduler.status().forcedDisabledReason())
                .contains("memoryThread.derivation.enabled requires extraction.item.graph");
    }

    private static MemoryThreadBuildScheduler scheduler(
            InMemoryMemoryStore store, Optional<String> forcedDisableReason) {
        return new MemoryThreadBuildScheduler(
                store.threadOperations(),
                store,
                new RuleBasedMemoryThreadDeriver(
                        MemoryThreadOptions.defaults()
                                .withEnabled(true)
                                .withDerivation(
                                        MemoryThreadDerivationOptions.defaults()
                                                .withEnabled(true))),
                new RuleBasedMemoryThreadTextGenerator(),
                MemoryThreadOptions.defaults()
                        .withEnabled(true)
                        .withDerivation(MemoryThreadDerivationOptions.defaults().withEnabled(true)),
                new NoopMemoryObserver(),
                forcedDisableReason);
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

    private static ItemEntityMention mention(MemoryId memoryId, long itemId, String entityKey) {
        return new ItemEntityMention(
                memoryId.toIdentifier(),
                itemId,
                entityKey,
                1.0f,
                Map.of("source", "test"),
                Instant.parse("2026-04-18T00:00:00Z"));
    }
}
