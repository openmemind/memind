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
package com.openmemind.ai.memory.core.store.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemOperationsRangeTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-05-09T00:00:00Z");

    @Test
    void defaultListItemsUpToReturnsDomainIdsInAscendingOrder() {
        ItemOperations ops =
                new StaticItemOperations(
                        List.of(item(8L), item(2L), item(null), item(5L), item(12L)));

        assertThat(ops.listItemsUpTo(MEMORY_ID, 8L))
                .extracting(MemoryItem::id)
                .containsExactly(2L, 5L, 8L);
    }

    @Test
    void defaultListItemsAfterHonorsDomainIdOrderAndLimit() {
        ItemOperations ops =
                new StaticItemOperations(List.of(item(30L), item(10L), item(20L), item(40L)));

        assertThat(ops.listItemsAfter(MEMORY_ID, 10L, 2))
                .extracting(MemoryItem::id)
                .containsExactly(20L, 30L);
        assertThat(ops.listItemsAfter(MEMORY_ID, 10L, 0)).isEmpty();
    }

    @Test
    void defaultMaxItemIdIsEmptyWhenNoDomainIdsExist() {
        assertThat(new StaticItemOperations(List.of(item(null))).maxItemId(MEMORY_ID)).isEmpty();
        assertThat(new StaticItemOperations(List.of(item(1L), item(7L))).maxItemId(MEMORY_ID))
                .hasValue(7L);
    }

    @Test
    void memoryStoreDefaultsItemCapabilitiesToDisabledAndInMemoryOverridesThem() {
        assertThat(ItemOperationsCapabilities.defaults().boundedIdLookup()).isFalse();
        assertThat(ItemOperationsCapabilities.defaults().boundedRangeReads()).isFalse();
        assertThat(ItemOperationsCapabilities.defaults().maxItemIdLookup()).isFalse();

        assertThat(new InMemoryMemoryStore().itemOperationsCapabilities().boundedIdLookup())
                .isTrue();
        assertThat(new InMemoryMemoryStore().itemOperationsCapabilities().boundedRangeReads())
                .isTrue();
        assertThat(new InMemoryMemoryStore().itemOperationsCapabilities().maxItemIdLookup())
                .isTrue();
    }

    private static MemoryItem item(Long id) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                id == null ? "null id" : "item " + id,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
                "conversation",
                id == null ? null : "vector-" + id,
                id == null ? null : "raw-" + id,
                id == null ? null : "hash-" + id,
                NOW,
                NOW,
                Map.of(),
                NOW,
                MemoryItemType.FACT);
    }

    private record StaticItemOperations(List<MemoryItem> items) implements ItemOperations {
        @Override
        public void insertItems(MemoryId id, List<MemoryItem> items) {}

        @Override
        public List<MemoryItem> getItemsByIds(MemoryId id, Collection<Long> itemIds) {
            return List.of();
        }

        @Override
        public List<MemoryItem> getItemsByVectorIds(MemoryId id, Collection<String> vectorIds) {
            return List.of();
        }

        @Override
        public List<MemoryItem> getItemsByContentHashes(
                MemoryId id, Collection<String> contentHashes) {
            return List.of();
        }

        @Override
        public List<MemoryItem> listItems(MemoryId id) {
            return items;
        }

        @Override
        public boolean hasItems(MemoryId id) {
            return !items.isEmpty();
        }

        @Override
        public void deleteItems(MemoryId id, Collection<Long> itemIds) {}
    }
}
