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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.MemoryThreadDerivationOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadRuleOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryThread;
import com.openmemind.ai.memory.core.data.MemoryThreadItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadStatus;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.thread.InMemoryMemoryThreadOperations;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleBasedMemoryThreadDeriverTest {

    @Test
    void deriveAttachesToOpenThreadWhenGraphSignalsBridgeIncomingItem() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        InMemoryMemoryThreadOperations threadOperations = new InMemoryMemoryThreadOperations();
        MemoryItem existing = item(101L, "Could not sleep for three nights after the breakup");
        MemoryItem incoming = item(102L, "Still feel pain when seeing their updates");
        store.itemOperations().insertItems(memoryId, List.of(existing, incoming));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 101L, "emotion:breakup"),
                                mention(memoryId, 102L, "emotion:breakup")));
        store.graphOperations()
                .upsertItemLinks(
                        memoryId,
                        List.of(link(memoryId, 102L, 101L, ItemLinkType.TEMPORAL, 0.92d)));
        threadOperations.upsertThreads(memoryId, List.of(openThread(memoryId, 201L, 101L)));
        threadOperations.upsertThreadItems(
                memoryId, List.of(membership(memoryId, 301L, 201L, 101L)));

        MemoryThreadDerivationOutcome outcome =
                new RuleBasedMemoryThreadDeriver(enabledOptions())
                        .derive(memoryId, List.of(incoming), threadOperations, store);

        assertThat(outcome.threads()).isEmpty();
        assertThat(outcome.memberships())
                .singleElement()
                .extracting(MemoryThreadItem::threadId)
                .isEqualTo(201L);
    }

    @Test
    void deriveCreatesNewThreadWhenNoExistingThreadQualifies() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        MemoryItem item = item(401L, "Started a different project kickoff");
        store.itemOperations().insertItems(memoryId, List.of(item));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId, List.of(mention(memoryId, 401L, "project:phase4")));

        MemoryThreadDerivationOutcome outcome =
                new RuleBasedMemoryThreadDeriver(enabledOptions())
                        .derive(
                                memoryId,
                                List.of(item),
                                new InMemoryMemoryThreadOperations(),
                                store);

        assertThat(outcome.threads())
                .singleElement()
                .extracting(MemoryThread::threadKey, MemoryThread::status)
                .containsExactly("ep:401", MemoryThreadStatus.OPEN);
        assertThat(outcome.memberships())
                .singleElement()
                .extracting(MemoryThreadItem::threadId)
                .isEqualTo(401L);
    }

    @Test
    void deriveReturnsNoAttachWhenSignalsStayBelowCreateThreshold() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        MemoryItem item =
                new MemoryItem(
                        501L,
                        memoryId.toIdentifier(),
                        "Had a vague bad day",
                        MemoryScope.USER,
                        MemoryCategory.PROFILE,
                        "conversation",
                        "vec-501",
                        "raw-501",
                        "hash-501",
                        null,
                        Instant.parse("2026-04-18T00:00:00Z"),
                        Map.of(),
                        Instant.parse("2026-04-18T00:00:00Z"),
                        MemoryItemType.FACT);
        store.itemOperations().insertItems(memoryId, List.of(item));

        MemoryThreadDerivationOutcome outcome =
                new RuleBasedMemoryThreadDeriver(
                                enabledOptions()
                                        .withRule(
                                                enabledOptions()
                                                        .rule()
                                                        .withNewThreadThreshold(0.90d)))
                        .derive(
                                memoryId,
                                List.of(item),
                                new InMemoryMemoryThreadOperations(),
                                store);

        assertThat(outcome.threads()).isEmpty();
        assertThat(outcome.memberships()).isEmpty();
    }

    private static MemoryThreadOptions enabledOptions() {
        return MemoryThreadOptions.defaults()
                .withEnabled(true)
                .withDerivation(MemoryThreadDerivationOptions.defaults().withEnabled(true))
                .withRule(new MemoryThreadRuleOptions(0.75d, 0.70d, 4, 32, 6));
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

    private static MemoryThread openThread(MemoryId memoryId, long id, long originItemId) {
        return new MemoryThread(
                id,
                memoryId.toIdentifier(),
                "ep:" + originItemId,
                "emotional_recovery",
                "Breakup Recovery Line",
                "From insomnia toward partial stabilization",
                MemoryThreadStatus.OPEN,
                0.88d,
                Instant.parse("2026-04-18T00:00:00Z"),
                null,
                Instant.parse("2026-04-18T00:00:00Z"),
                originItemId,
                originItemId,
                1,
                Map.of(),
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                false);
    }

    private static MemoryThreadItem membership(
            MemoryId memoryId, long id, long threadId, long itemId) {
        return new MemoryThreadItem(
                id,
                memoryId.toIdentifier(),
                threadId,
                itemId,
                0.95d,
                MemoryThreadRole.CORE,
                1,
                Instant.parse("2026-04-18T00:00:00Z"),
                Map.of(),
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                false);
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

    private static ItemLink link(
            MemoryId memoryId,
            long sourceItemId,
            long targetItemId,
            ItemLinkType type,
            double strength) {
        return new ItemLink(
                memoryId.toIdentifier(),
                sourceItemId,
                targetItemId,
                type,
                strength,
                Map.of("source", "test"),
                Instant.parse("2026-04-18T00:00:00Z"));
    }
}
