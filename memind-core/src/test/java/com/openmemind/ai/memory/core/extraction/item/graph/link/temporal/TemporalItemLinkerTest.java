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
package com.openmemind.ai.memory.core.extraction.item.graph.link.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.store.item.TemporalCandidateMatch;
import com.openmemind.ai.memory.core.store.item.TemporalCandidateRequest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class TemporalItemLinkerTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void linkerShouldMergeSameBatchAndHistoricalCandidatesUnderPerIncomingQuota() {
        var itemOps = new InMemoryItemOperations();
        var graphOps = new InMemoryGraphOperations();
        itemOps.insertItems(
                MEMORY_ID,
                List.of(
                        temporalItem(
                                1L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T09:00:00Z"),
                        rangedItem(
                                2L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T09:30:00Z",
                                "2026-04-10T11:00:00Z")));

        var linker =
                new TemporalItemLinker(
                        itemOps,
                        graphOps,
                        new TemporalRelationClassifier(),
                        new ItemGraphOptions(true, 8, 2, 1, 5, 0.82d, 4, 1, 128));

        var incoming =
                List.of(
                        temporalItem(
                                101L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T10:00:00Z"),
                        temporalItem(
                                102L,
                                MemoryItemType.FACT,
                                MemoryCategory.PROFILE,
                                "2026-04-10T10:05:00Z"));

        var stats = linker.link(MEMORY_ID, incoming).block();

        assertThat(stats.selectedPairCount()).isEqualTo(1);
        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .extracting(
                        ItemLink::sourceItemId,
                        ItemLink::targetItemId,
                        link -> link.metadata().get("relationType"))
                .containsExactly(tuple(2L, 101L, "overlap"));
    }

    @Test
    void linkerShouldDegradeOnlyTheFailedHistoricalLookupSubBatch() {
        var itemOps =
                new InMemoryItemOperations() {
                    private int calls;

                    @Override
                    public List<TemporalCandidateMatch> listTemporalCandidateMatches(
                            MemoryId memoryId,
                            List<TemporalCandidateRequest> requests,
                            Collection<Long> excludeItemIds) {
                        calls++;
                        if (calls == 2) {
                            throw new IllegalStateException("boom");
                        }
                        return super.listTemporalCandidateMatches(
                                memoryId, requests, excludeItemIds);
                    }
                };
        var linker =
                new TemporalItemLinker(
                        itemOps,
                        new InMemoryGraphOperations(),
                        new TemporalRelationClassifier(),
                        ItemGraphOptions.defaults().withEnabled(true));

        var incoming =
                LongStream.rangeClosed(1, 129).mapToObj(TemporalItemLinkerTest::batchItem).toList();

        var stats = linker.link(MEMORY_ID, incoming).block();

        assertThat(stats.historyQueryBatchCount()).isEqualTo(2);
        assertThat(stats.degraded()).isTrue();
    }

    @Test
    void linkerComputesStrengthsForOverlapNearbyAndBeforeRelations() {
        var itemOps = new InMemoryItemOperations();
        var graphOps = new InMemoryGraphOperations();
        itemOps.insertItems(
                MEMORY_ID,
                List.of(
                        rangedItem(
                                1L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T09:00:00Z",
                                "2026-04-10T11:00:00Z")));
        var linker =
                new TemporalItemLinker(
                        itemOps,
                        graphOps,
                        new TemporalRelationClassifier(),
                        new ItemGraphOptions(true, 8, 2, 1, 5, 0.82d, 4, 1, 128));
        var incoming =
                List.of(
                        temporalItem(
                                101L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T10:00:00Z"),
                        temporalItem(
                                201L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-11T10:00:00Z"),
                        temporalItem(
                                301L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-20T10:00:00Z"));

        var stats = linker.link(MEMORY_ID, incoming).block();

        assertThat(stats.createdLinkCount()).isEqualTo(3);
        assertThat(stats.belowRetrievalFloorCount()).isEqualTo(0);
        assertThat(stats.minStrength()).isEqualTo(0.60d);
        assertThat(stats.maxStrength()).isEqualTo(1.0d);
        assertThat(stats.strengthBucketSummary()).isEqualTo("0.60-0.74=1,0.75-0.89=1,0.90-1.00=1");
        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .extracting(link -> link.metadata().get("relationType"), ItemLink::strength)
                .containsExactly(
                        tuple("overlap", 1.0d), tuple("nearby", 0.75d), tuple("before", 0.60d));
    }

    @Test
    void temporalLinkingStatsBuilderRoundTripsRolloutFields() {
        var stats =
                new TemporalItemLinker.TemporalLinkingStats(
                        2,
                        1,
                        4,
                        3,
                        2,
                        2,
                        10L,
                        11L,
                        12L,
                        1,
                        0.60d,
                        1.0d,
                        "0.60-0.74=1,0.90-1.00=1",
                        false);

        assertThat(stats.toBuilder().build()).isEqualTo(stats);
    }

    private static MemoryItem temporalItem(
            Long id, MemoryItemType type, MemoryCategory category, String occurredAt) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "item-" + id,
                MemoryScope.USER,
                category,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                Instant.parse(occurredAt),
                CREATED_AT,
                Map.of(),
                CREATED_AT,
                type);
    }

    private static MemoryItem rangedItem(
            Long id, MemoryItemType type, MemoryCategory category, String start, String end) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "item-" + id,
                MemoryScope.USER,
                category,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                null,
                Instant.parse(start),
                Instant.parse(end),
                "unknown",
                CREATED_AT,
                Map.of(),
                CREATED_AT,
                type);
    }

    private static MemoryItem batchItem(long id) {
        return temporalItem(
                id,
                MemoryItemType.FACT,
                MemoryCategory.EVENT,
                Instant.parse("2026-04-10T00:00:00Z").plusSeconds(id * 60L).toString());
    }
}
