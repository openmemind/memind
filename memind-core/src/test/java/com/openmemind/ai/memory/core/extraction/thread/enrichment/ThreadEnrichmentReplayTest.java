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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEnrichmentInput;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThreadEnrichmentReplayTest {

    @Test
    void replayOrderUsesBasisFieldsAndEntrySeqInsteadOfCreatedAt() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadMaterializationPolicy policy = ThreadMaterializationPolicy.v1();
        seedTopicThread(store, memoryId);
        store.itemOperations()
                .insertItems(memoryId, List.of(item(303L, "The user finalized the itinerary.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId, List.of(mention(memoryId, 303L, "concept:travel")));

        ThreadEnrichmentInputStore inputStore = store.threadEnrichmentInputStore();
        inputStore.appendRunAndEnqueueReplay(
                memoryId,
                303L,
                List.of(
                        headlineRefreshInput(
                                memoryId,
                                303L,
                                3L,
                                "topic:topic:concept:travel|303|3|" + policy.version(),
                                0,
                                "Headline from cutoff 303",
                                Instant.parse("2026-04-22T09:59:00Z"),
                                policy.version())));
        inputStore.appendRunAndEnqueueReplay(
                memoryId,
                302L,
                List.of(
                        headlineRefreshInput(
                                memoryId,
                                302L,
                                2L,
                                "topic:topic:concept:travel|302|2|" + policy.version(),
                                1,
                                "Second entry from cutoff 302",
                                Instant.parse("2026-04-22T10:30:00Z"),
                                policy.version()),
                        headlineRefreshInput(
                                memoryId,
                                302L,
                                2L,
                                "topic:topic:concept:travel|302|2|" + policy.version(),
                                0,
                                "First entry from cutoff 302",
                                Instant.parse("2026-04-22T10:31:00Z"),
                                policy.version())));

        ThreadProjectionMaterializer materializer =
                new ThreadProjectionMaterializer(
                        store.itemOperations(),
                        store.graphOperations(),
                        inputStore,
                        policy);

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 303L);

        assertThat(projection.events())
                .filteredOn(event -> event.eventKey().contains(":enrichment:"))
                .extracting(event -> event.eventPayloadJson().get("summary"))
                .containsExactly(
                        "First entry from cutoff 302",
                        "Second entry from cutoff 302",
                        "Headline from cutoff 303");
    }

    @Test
    void headlineRefreshDoesNotAdvanceLastEventAtBeyondSupportingItemEvidence() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ThreadMaterializationPolicy policy = ThreadMaterializationPolicy.v1();
        seedTopicThread(store, memoryId);
        store.threadEnrichmentInputStore()
                .appendRunAndEnqueueReplay(
                        memoryId,
                        302L,
                        List.of(
                                headlineRefreshInput(
                                        memoryId,
                                        302L,
                                        2L,
                                        "topic:topic:concept:travel|302|2|" + policy.version(),
                                        0,
                                        "Refreshed travel headline",
                                        Instant.parse("2026-04-22T10:30:00Z"),
                                        policy.version())));

        ThreadProjectionMaterializer materializer =
                new ThreadProjectionMaterializer(
                        store.itemOperations(),
                        store.graphOperations(),
                        store.threadEnrichmentInputStore(),
                        policy);

        ThreadProjectionMaterializer.MaterializedProjection projection =
                materializer.materializeUpTo(memoryId, 302L);

        assertThat(projection.threads())
                .singleElement()
                .satisfies(
                        thread -> {
                            assertThat(thread.headline()).isEqualTo("Refreshed travel headline");
                            assertThat(thread.lastEventAt())
                                    .isEqualTo(Instant.parse("2026-04-20T09:00:00Z"));
                            assertThat(thread.lastMeaningfulUpdateAt()).isNull();
                        });
        assertThat(projection.events())
                .filteredOn(event -> event.eventKey().contains(":enrichment:"))
                .singleElement()
                .satisfies(
                        event -> {
                            assertThat(event.createdAt())
                                    .isEqualTo(Instant.parse("2026-04-22T10:30:00Z"));
                            assertThat(event.eventTime())
                                    .isEqualTo(Instant.parse("2026-04-20T09:00:00Z"));
                        });
    }

    private static void seedTopicThread(InMemoryMemoryStore store, MemoryId memoryId) {
        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(301L, "The user planned a trip."),
                                item(302L, "The user booked the trip.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 301L, "concept:travel"),
                                mention(memoryId, 302L, "concept:travel")));
    }

    private static MemoryThreadEnrichmentInput headlineRefreshInput(
            MemoryId memoryId,
            long basisCutoffItemId,
            long basisMeaningfulEventCount,
            String inputRunKey,
            int entrySeq,
            String summary,
            Instant createdAt,
            String policyVersion) {
        return new MemoryThreadEnrichmentInput(
                memoryId.toIdentifier(),
                "topic:topic:concept:travel",
                inputRunKey,
                entrySeq,
                basisCutoffItemId,
                basisMeaningfulEventCount,
                policyVersion,
                Map.of(
                        "eventType",
                        "OBSERVATION",
                        "meaningful",
                        false,
                        "basisEventKey",
                        "topic:topic:concept:travel:observation:" + basisCutoffItemId,
                        "summary",
                        summary,
                        "summaryRole",
                        "HEADLINE_REFRESH"),
                Map.of(
                        "sourceType",
                        "THREAD_LLM",
                        "supportingItemIds",
                        List.of(basisCutoffItemId)),
                createdAt);
    }

    private static MemoryItem item(long itemId, String content) {
        return new MemoryItem(
                itemId,
                TestMemoryIds.userAgent().toIdentifier(),
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

    private static ItemEntityMention mention(MemoryId memoryId, long itemId, String entityKey) {
        return new ItemEntityMention(
                memoryId.toIdentifier(),
                itemId,
                entityKey,
                1.0f,
                Map.of("source", "test"),
                Instant.parse("2026-04-20T09:00:00Z"));
    }
}
