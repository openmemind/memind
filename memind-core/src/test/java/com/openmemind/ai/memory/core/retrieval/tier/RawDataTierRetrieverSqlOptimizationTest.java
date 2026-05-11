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
package com.openmemind.ai.memory.core.retrieval.tier;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.deep.ExpandedQuery;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InMemoryInsightOperations;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RawDataTierRetrieverSqlOptimizationTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-05-11T00:00:00Z");

    @org.junit.jupiter.api.Test
    void retrieveHydratesOriginalAndExpandedVectorHitsWithOneBatchedLookup() {
        var rawDataOps = new RecordingRawDataOperations();
        rawDataOps.rows.put("vec-original", rawData("rd-original", "vec-original"));
        rawDataOps.rows.put("vec-expanded", rawData("rd-expanded", "vec-expanded"));
        rawDataOps.rows.put("vec-hyde", rawData("rd-hyde", "vec-hyde"));
        var vector = new QueryAwareVector();
        vector.results.put("query", List.of(hit("vec-original", 0.91f)));
        vector.results.put("expanded vec", List.of(hit("vec-expanded", 0.89f)));
        vector.results.put("expanded hyde", List.of(hit("vec-hyde", 0.87f)));
        var retriever = new RawDataTierRetriever(store(rawDataOps), vector);

        StepVerifier.create(
                        retriever.retrieve(
                                context("query"),
                                RetrievalConfig.simple(),
                                List.of(),
                                List.of(
                                        new ExpandedQuery(
                                                ExpandedQuery.QueryType.VEC, "expanded vec"),
                                        new ExpandedQuery(
                                                ExpandedQuery.QueryType.HYDE, "expanded hyde"))))
                .assertNext(
                        result -> {
                            assertThat(result.results())
                                    .extracting(ScoredResult::sourceId)
                                    .contains("rd-original", "rd-expanded", "rd-hyde");
                            assertThat(rawDataOps.listRawDataCalls).isZero();
                            assertThat(rawDataOps.captionVectorLookupCalls).isEqualTo(1);
                            assertThat(rawDataOps.requestedVectorIds.getFirst())
                                    .containsExactlyInAnyOrder(
                                            "vec-original", "vec-expanded", "vec-hyde");
                        })
                .verifyComplete();
    }

    @org.junit.jupiter.api.Test
    void retrieveAppliesScopeHintsAfterVectorIdHydration() {
        var rawDataOps = new RecordingRawDataOperations();
        rawDataOps.rows.put("vec-in-scope", rawData("rd-in-scope", "vec-in-scope"));
        rawDataOps.rows.put("vec-out-of-scope", rawData("rd-out-of-scope", "vec-out-of-scope"));
        var vector = new QueryAwareVector();
        vector.results.put(
                "query", List.of(hit("vec-in-scope", 0.91f), hit("vec-out-of-scope", 0.90f)));
        var retriever = new RawDataTierRetriever(store(rawDataOps), vector);

        StepVerifier.create(
                        retriever.retrieve(
                                context("query"),
                                RetrievalConfig.simple(),
                                List.of("rd-in-scope"),
                                List.of()))
                .assertNext(
                        result ->
                                assertThat(result.results())
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("rd-in-scope"))
                .verifyComplete();
        assertThat(rawDataOps.listRawDataCalls).isZero();
    }

    @org.junit.jupiter.api.Test
    void searchByVectorDoesNotListAllRawData() {
        var rawDataOps = new RecordingRawDataOperations();
        rawDataOps.rows.put("vec-1", rawData("rd-1", "vec-1"));
        var vector = new QueryAwareVector();
        vector.results.put("query", List.of(hit("vec-1", 0.91f)));
        var retriever = new RawDataTierRetriever(store(rawDataOps), vector);

        StepVerifier.create(
                        retriever.searchByVector(
                                context("query"),
                                RetrievalConfig.simple().tier3(),
                                ScoringConfig.defaults()))
                .assertNext(
                        results ->
                                assertThat(results)
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("rd-1"))
                .verifyComplete();
        assertThat(rawDataOps.listRawDataCalls).isZero();
        assertThat(rawDataOps.captionVectorLookupCalls).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    void searchHybridDoesNotListAllRawDataForVectorSide() {
        var rawDataOps = new RecordingRawDataOperations();
        rawDataOps.rows.put("vec-1", rawData("rd-1", "vec-1"));
        var vector = new QueryAwareVector();
        vector.results.put("query", List.of(hit("vec-1", 0.91f)));
        MemoryTextSearch textSearch = (memoryId, query, topK, target) -> Mono.just(List.of());
        var retriever = new RawDataTierRetriever(store(rawDataOps), vector, textSearch);

        StepVerifier.create(
                        retriever.searchHybrid(
                                context("query"),
                                RetrievalConfig.simple().tier3(),
                                ScoringConfig.defaults()))
                .assertNext(
                        results ->
                                assertThat(results)
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("rd-1"))
                .verifyComplete();
        assertThat(rawDataOps.listRawDataCalls).isZero();
        assertThat(rawDataOps.captionVectorLookupCalls).isEqualTo(1);
    }

    private static MemoryStore store(RawDataOperations rawDataOperations) {
        return MemoryStore.of(
                rawDataOperations, new InMemoryItemOperations(), new InMemoryInsightOperations());
    }

    private static QueryContext context(String searchQuery) {
        return new QueryContext(
                MEMORY_ID, searchQuery, searchQuery, List.of(), Map.of(), null, null);
    }

    private static VectorSearchResult hit(String vectorId, float score) {
        return new VectorSearchResult(vectorId, "text " + vectorId, score, Map.of());
    }

    private static MemoryRawData rawData(String id, String captionVectorId) {
        return new MemoryRawData(
                id,
                MEMORY_ID.toIdentifier(),
                ConversationContent.TYPE,
                "content-" + id,
                new Segment("content " + id, "caption " + id, new CharBoundary(0, 7), Map.of()),
                "caption " + id,
                captionVectorId,
                Map.of(),
                null,
                null,
                NOW,
                NOW,
                NOW);
    }

    private static final class QueryAwareVector implements MemoryVector {
        private final Map<String, List<VectorSearchResult>> results = new HashMap<>();

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            return Mono.just("stored-vector");
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            return Mono.just(List.of());
        }

        @Override
        public Mono<Void> delete(MemoryId memoryId, String vectorId) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteBatch(MemoryId memoryId, List<String> vectorIds) {
            return Mono.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK) {
            return search(memoryId, query, topK, Map.of());
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.fromIterable(results.getOrDefault(query, List.of()));
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId,
                String query,
                int topK,
                double minScore,
                Map<String, Object> filter) {
            return Flux.fromIterable(results.getOrDefault(query, List.of()))
                    .filter(hit -> hit.score() >= minScore);
        }

        @Override
        public Mono<List<Float>> embed(String text) {
            return Mono.just(List.of());
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.just(List.of());
        }
    }

    private static final class RecordingRawDataOperations implements RawDataOperations {
        private final Map<String, MemoryRawData> rows = new HashMap<>();
        private final List<List<String>> requestedVectorIds = new ArrayList<>();
        private int listRawDataCalls;
        private int captionVectorLookupCalls;

        @Override
        public List<MemoryRawData> getRawDataByCaptionVectorIds(
                MemoryId id, Collection<String> captionVectorIds) {
            captionVectorLookupCalls++;
            List<String> ids = captionVectorIds.stream().toList();
            requestedVectorIds.add(ids);
            return ids.stream().map(rows::get).filter(Objects::nonNull).toList();
        }

        @Override
        public void upsertRawData(MemoryId id, List<MemoryRawData> rawDataList) {}

        @Override
        public Optional<MemoryRawData> getRawData(MemoryId id, String rawDataId) {
            return rows.values().stream().filter(row -> row.id().equals(rawDataId)).findFirst();
        }

        @Override
        public Optional<MemoryRawData> getRawDataByContentId(MemoryId id, String contentId) {
            return Optional.empty();
        }

        @Override
        public List<MemoryRawData> listRawData(MemoryId id) {
            listRawDataCalls++;
            throw new AssertionError("broad raw-data scan must not be used");
        }

        @Override
        public List<MemoryRawData> pollRawDataWithoutVector(
                MemoryId id, int limit, Duration minAge) {
            return List.of();
        }

        @Override
        public void updateRawDataVectorIds(
                MemoryId id, Map<String, String> vectorIds, Map<String, Object> metadataPatch) {}
    }
}
