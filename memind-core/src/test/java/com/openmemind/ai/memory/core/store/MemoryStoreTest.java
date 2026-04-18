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
package com.openmemind.ai.memory.core.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.MemoryResource;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.store.insight.InMemoryInsightOperations;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.InMemoryRawDataOperations;
import com.openmemind.ai.memory.core.store.resource.InMemoryResourceOperations;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryStoreTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");

    @Test
    void legacyFactoryShouldFailFastForResourceOperations() {
        var store =
                MemoryStore.of(
                        new InMemoryRawDataOperations(),
                        new InMemoryItemOperations(),
                        new InMemoryInsightOperations());

        assertThatThrownBy(store::resourceOperations)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ResourceOperations is required");
    }

    @Test
    void legacyFactoryShouldExposeNoOpThreadOperations() {
        var store =
                MemoryStore.of(
                        new InMemoryRawDataOperations(),
                        new InMemoryItemOperations(),
                        new InMemoryInsightOperations());

        store.threadOperations().upsertThreads(MEMORY_ID, List.of());
        store.threadOperations().upsertThreadItems(MEMORY_ID, List.of());
        store.threadOperations().deleteMembershipsByItemIds(MEMORY_ID, List.of(1L));

        assertThat(store.threadOperations().listThreads(MEMORY_ID)).isEmpty();
        assertThat(store.threadOperations().listThreadItems(MEMORY_ID)).isEmpty();
    }

    @Test
    void extendedFactoryShouldExposeResourceOperationsAndResourceStore() {
        var resourceOperations = new InMemoryResourceOperations();
        ResourceStore resourceStore = new InMemoryResourceStoreStub();
        var store =
                MemoryStore.of(
                        new InMemoryRawDataOperations(),
                        new InMemoryItemOperations(),
                        new InMemoryInsightOperations(),
                        resourceOperations,
                        resourceStore);

        assertThat(store.resourceOperations()).isSameAs(resourceOperations);
        assertThat(store.resourceStore()).isSameAs(resourceStore);
    }

    @Test
    void upsertRawDataWithResourcesShouldPersistBothSides() {
        var resourceOperations = new InMemoryResourceOperations();
        var rawDataOperations = new InMemoryRawDataOperations();
        var store =
                MemoryStore.of(
                        rawDataOperations,
                        new InMemoryItemOperations(),
                        new InMemoryInsightOperations(),
                        resourceOperations,
                        null);
        var resource =
                new MemoryResource(
                        "res-1",
                        MEMORY_ID.toIdentifier(),
                        "file:///tmp/report.pdf",
                        null,
                        "report.pdf",
                        "application/pdf",
                        "abc",
                        123L,
                        Map.of(),
                        Instant.parse("2026-04-09T00:00:00Z"));
        var rawData =
                new MemoryRawData(
                        "raw-1",
                        MEMORY_ID.toIdentifier(),
                        "DOCUMENT",
                        "content-1",
                        Segment.single("hello"),
                        "caption",
                        null,
                        Map.of(),
                        null,
                        null,
                        Instant.parse("2026-04-09T00:00:00Z"),
                        Instant.parse("2026-04-09T00:00:00Z"),
                        Instant.parse("2026-04-09T00:00:01Z"));

        store.upsertRawDataWithResources(MEMORY_ID, List.of(resource), List.of(rawData));

        assertThat(store.resourceOperations().getResource(MEMORY_ID, "res-1")).contains(resource);
        assertThat(store.rawDataOperations().getRawData(MEMORY_ID, "raw-1")).contains(rawData);
    }

    private static final class InMemoryResourceStoreStub implements ResourceStore {

        @Override
        public reactor.core.publisher.Mono<com.openmemind.ai.memory.core.resource.ResourceRef>
                store(
                        com.openmemind.ai.memory.core.data.MemoryId memoryId,
                        String fileName,
                        byte[] data,
                        String mimeType,
                        Map<String, Object> metadata) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public reactor.core.publisher.Mono<byte[]> retrieve(
                com.openmemind.ai.memory.core.resource.ResourceRef ref) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public reactor.core.publisher.Mono<Void> delete(
                com.openmemind.ai.memory.core.resource.ResourceRef ref) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public reactor.core.publisher.Mono<Boolean> exists(
                com.openmemind.ai.memory.core.resource.ResourceRef ref) {
            return reactor.core.publisher.Mono.just(false);
        }
    }
}
