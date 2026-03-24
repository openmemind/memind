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
package com.openmemind.ai.memory.core.extraction.item.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SemanticDeduplicatorTest {

    private final MemoryId memoryId = DefaultMemoryId.of("user1", "agent1");

    @org.junit.jupiter.api.Test
    void batchLoadsMatchedItemsByVectorIdsWithoutScanningAllItems() {
        var store = new TrackingMemoryStore();
        var matchedItem = memoryItem(1L, "v-1", "existing item");
        store.itemOperations().insertItems(memoryId, List.of(matchedItem));

        var vector = new StubMemoryVector();
        vector.register("first", new VectorSearchResult("v-1", "existing item", 0.95f, Map.of()));
        vector.register("second", new VectorSearchResult("v-1", "existing item", 0.91f, Map.of()));

        var deduplicator = new SemanticDeduplicator(store, vector, 0.8);
        var entries = List.of(entry("first"), entry("second"));

        StepVerifier.create(deduplicator.deduplicate(memoryId, entries))
                .assertNext(
                        result -> {
                            assertThat(result.newEntries()).isEmpty();
                            assertThat(result.matchedItems()).hasSize(2);
                            assertThat(result.matchedItems())
                                    .extracting(DeduplicationResult.MatchedItem::item)
                                    .containsOnly(matchedItem);
                        })
                .verifyComplete();

        assertThat(store.getItemsByVectorIdsCalls()).isEqualTo(1);
        assertThat(store.lastRequestedVectorIds()).containsExactly("v-1");
        assertThat(store.getAllItemsCalls()).isZero();
    }

    @org.junit.jupiter.api.Test
    void keepsEntryWhenMatchedVectorIdHasNoBackingItem() {
        var store = new TrackingMemoryStore();
        store.itemOperations()
                .insertItems(memoryId, List.of(memoryItem(1L, "other-vector", "existing item")));

        var vector = new StubMemoryVector();
        vector.register(
                "missing", new VectorSearchResult("missing-vector", "ghost", 0.92f, Map.of()));

        var deduplicator = new SemanticDeduplicator(store, vector, 0.8);
        var input = entry("missing");

        StepVerifier.create(deduplicator.deduplicate(memoryId, List.of(input)))
                .assertNext(
                        result -> {
                            assertThat(result.newEntries()).containsExactly(input);
                            assertThat(result.matchedItems()).isEmpty();
                        })
                .verifyComplete();

        assertThat(store.getItemsByVectorIdsCalls()).isEqualTo(1);
        assertThat(store.getAllItemsCalls()).isZero();
    }

    @org.junit.jupiter.api.Test
    void skipsBatchLookupWhenSearchFindsNoSimilarItem() {
        var store = new TrackingMemoryStore();
        store.itemOperations()
                .insertItems(memoryId, List.of(memoryItem(1L, "v-1", "existing item")));

        var vector = new StubMemoryVector();
        var deduplicator = new SemanticDeduplicator(store, vector, 0.8);
        var input = entry("new fact");

        StepVerifier.create(deduplicator.deduplicate(memoryId, List.of(input)))
                .assertNext(
                        result -> {
                            assertThat(result.newEntries()).containsExactly(input);
                            assertThat(result.matchedItems()).isEmpty();
                        })
                .verifyComplete();

        assertThat(store.getItemsByVectorIdsCalls()).isZero();
        assertThat(store.getAllItemsCalls()).isZero();
    }

    private static ExtractedMemoryEntry entry(String content) {
        return new ExtractedMemoryEntry(
                content,
                0.9f,
                Instant.parse("2026-03-20T00:00:00Z"),
                "raw-1",
                null,
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                MemoryCategory.PROFILE.name());
    }

    private static MemoryItem memoryItem(Long id, String vectorId, String content) {
        return new MemoryItem(
                id,
                "user1:agent1",
                content,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
                "conversation",
                vectorId,
                "raw-1",
                "hash-" + id,
                Instant.parse("2026-03-20T00:00:00Z"),
                Map.of(),
                Instant.parse("2026-03-20T00:00:00Z"),
                MemoryItemType.FACT);
    }

    private static final class TrackingMemoryStore extends InMemoryMemoryStore {
        private int getAllItemsCalls;
        private int getItemsByVectorIdsCalls;
        private List<String> lastRequestedVectorIds = List.of();
        private final ItemOperations trackingItemOps;

        TrackingMemoryStore() {
            var delegate = super.itemOperations();
            trackingItemOps =
                    new ItemOperations() {
                        @Override
                        public void insertItems(MemoryId id, List<MemoryItem> items) {
                            delegate.insertItems(id, items);
                        }

                        @Override
                        public List<MemoryItem> getItemsByIds(
                                MemoryId id, Collection<Long> itemIds) {
                            return delegate.getItemsByIds(id, itemIds);
                        }

                        @Override
                        public List<MemoryItem> getItemsByVectorIds(
                                MemoryId id, Collection<String> vectorIds) {
                            getItemsByVectorIdsCalls++;
                            lastRequestedVectorIds = List.copyOf(vectorIds);
                            return delegate.getItemsByVectorIds(id, vectorIds);
                        }

                        @Override
                        public List<MemoryItem> getItemsByContentHashes(
                                MemoryId id, Collection<String> contentHashes) {
                            return delegate.getItemsByContentHashes(id, contentHashes);
                        }

                        @Override
                        public List<MemoryItem> listItems(MemoryId id) {
                            getAllItemsCalls++;
                            return delegate.listItems(id);
                        }

                        @Override
                        public boolean hasItems(MemoryId id) {
                            return delegate.hasItems(id);
                        }

                        @Override
                        public void deleteItems(MemoryId id, Collection<Long> itemIds) {
                            delegate.deleteItems(id, itemIds);
                        }
                    };
        }

        @Override
        public ItemOperations itemOperations() {
            return trackingItemOps;
        }

        int getAllItemsCalls() {
            return getAllItemsCalls;
        }

        int getItemsByVectorIdsCalls() {
            return getItemsByVectorIdsCalls;
        }

        List<String> lastRequestedVectorIds() {
            return lastRequestedVectorIds;
        }
    }

    private static final class StubMemoryVector implements MemoryVector {
        private final Map<String, List<VectorSearchResult>> results = new HashMap<>();

        void register(String query, VectorSearchResult result) {
            results.put(query, List.of(result));
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.fromIterable(results.getOrDefault(query, List.of()));
        }

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Void> delete(MemoryId memoryId, String vectorId) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Void> deleteBatch(MemoryId memoryId, List<String> vectorIds) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK) {
            return search(memoryId, query, topK, Map.of());
        }

        @Override
        public Mono<List<Float>> embed(String text) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.error(new UnsupportedOperationException());
        }
    }
}
