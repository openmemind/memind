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
package com.openmemind.ai.memory.core.extraction.item.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultItemGraphMaterializerTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void semanticLinksShouldUseSameEmbeddingTextRuleAsItemVectorization() {
        var graphOps = new InMemoryGraphOperations();
        var vector = new StubMemoryVector();
        vector.register(
                "Use when asked about OpenAI rollout",
                new VectorSearchResult("vector-77", "existing note", 0.93f, Map.of()));
        var itemOps = new InMemoryItemOperations();
        itemOps.insertItems(
                MEMORY_ID,
                List.of(existingItem(77L, "vector-77", "Existing OpenAI deployment note")));

        var materializer =
                new DefaultItemGraphMaterializer(
                        graphOps,
                        new GraphHintNormalizer(),
                        new SemanticItemLinker(
                                itemOps,
                                graphOps,
                                vector,
                                ItemGraphOptions.defaults().withEnabled(true)),
                        ItemGraphOptions.defaults().withEnabled(true));

        var item =
                newItem(
                        101L,
                        "vector-101",
                        "User discussed OpenAI deployment",
                        Map.of("whenToUse", "Use when asked about OpenAI rollout"));

        StepVerifier.create(materializer.materialize(MEMORY_ID, List.of(item), List.of(newEntry())))
                .verifyComplete();

        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .extracting(ItemLink::linkType, ItemLink::sourceItemId, ItemLink::targetItemId)
                .contains(tuple(ItemLinkType.SEMANTIC, 101L, 77L));
    }

    @Test
    void repeatedMaterializeShouldNotDuplicateMentionsLinksOrInflateCooccurrences() {
        var graphOps = new InMemoryGraphOperations();
        var materializer = materializer(graphOps);
        var items =
                List.of(
                        newItem(101L, "vector-101", "Cause item", Map.of()),
                        newItem(102L, "vector-102", "Effect item", Map.of()));
        var entries =
                List.of(entryWithSharedEntities(), entryWithSharedEntitiesAndCausalReference());

        StepVerifier.create(materializer.materialize(MEMORY_ID, items, entries)).verifyComplete();
        StepVerifier.create(materializer.materialize(MEMORY_ID, items, entries)).verifyComplete();

        assertThat(graphOps.listItemEntityMentions(MEMORY_ID)).hasSize(4);
        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .filteredOn(link -> link.linkType() != ItemLinkType.SEMANTIC)
                .hasSize(2);
        assertThat(graphOps.listEntityCooccurrences(MEMORY_ID))
                .singleElement()
                .extracting(EntityCooccurrence::cooccurrenceCount)
                .isEqualTo(2);
    }

    private static DefaultItemGraphMaterializer materializer(InMemoryGraphOperations graphOps) {
        return new DefaultItemGraphMaterializer(
                graphOps,
                new GraphHintNormalizer(),
                new SemanticItemLinker(
                        new InMemoryItemOperations(),
                        graphOps,
                        new StubMemoryVector(),
                        ItemGraphOptions.defaults().withEnabled(true)),
                ItemGraphOptions.defaults().withEnabled(true));
    }

    private static MemoryItem existingItem(Long id, String vectorId, String content) {
        return newItem(id, vectorId, content, Map.of());
    }

    private static MemoryItem newItem(
            Long id, String vectorId, String content, Map<String, Object> metadata) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                vectorId,
                "raw-" + id,
                "hash-" + id,
                Instant.parse("2026-04-16T09:00:00Z").plusSeconds(id),
                CREATED_AT,
                metadata,
                CREATED_AT,
                MemoryItemType.FACT);
    }

    private static ExtractedMemoryEntry newEntry() {
        return new ExtractedMemoryEntry(
                "User discussed OpenAI deployment",
                1.0f,
                null,
                null,
                null,
                null,
                CREATED_AT,
                "raw-1",
                null,
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event",
                ExtractedGraphHints.empty());
    }

    private static ExtractedMemoryEntry entryWithSharedEntities() {
        return new ExtractedMemoryEntry(
                "Cause item",
                1.0f,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-16T09:00:00Z"),
                "raw-1",
                null,
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event",
                new ExtractedGraphHints(
                        List.of(
                                new ExtractedGraphHints.ExtractedEntityHint(
                                        "OpenAI", "organization", 0.95f),
                                new ExtractedGraphHints.ExtractedEntityHint(
                                        "Sam Altman", "person", 0.88f)),
                        List.of()));
    }

    private static ExtractedMemoryEntry entryWithSharedEntitiesAndCausalReference() {
        return new ExtractedMemoryEntry(
                "Effect item",
                1.0f,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-16T10:00:00Z"),
                "raw-2",
                null,
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event",
                new ExtractedGraphHints(
                        List.of(
                                new ExtractedGraphHints.ExtractedEntityHint(
                                        "OpenAI", "organization", 0.95f),
                                new ExtractedGraphHints.ExtractedEntityHint(
                                        "Sam Altman", "person", 0.88f)),
                        List.of(
                                new ExtractedGraphHints.ExtractedCausalRelationHint(
                                        0, "caused_by", 0.91f))));
    }

    private static final class StubMemoryVector implements MemoryVector {

        private final Map<String, List<VectorSearchResult>> results = new HashMap<>();

        private void register(String query, VectorSearchResult result) {
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
