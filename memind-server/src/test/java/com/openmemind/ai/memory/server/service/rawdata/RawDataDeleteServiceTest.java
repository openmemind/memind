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
package com.openmemind.ai.memory.server.service.rawdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.context.ContextRequest;
import com.openmemind.ai.memory.core.extraction.context.ContextWindow;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.item.query.ItemPageQuery;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.domain.rawdata.response.RawDataDeleteResult;
import com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView;
import com.openmemind.ai.memory.server.mapper.item.AdminItemQueryMapper;
import com.openmemind.ai.memory.server.mapper.rawdata.AdminRawDataQueryMapper;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeUnavailableException;
import com.openmemind.ai.memory.server.runtime.RuntimeHandle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RawDataDeleteServiceTest {

    @Test
    void deleteRawDataRemovesLinkedItemsButKeepsInsights() {
        FakeRawDataQueryMapper rawDataQueryMapper = new FakeRawDataQueryMapper();
        rawDataQueryMapper.findByBizIdsResult =
                List.of(rawData("rd-1", "u1", "a1", "u1:a1", "cap-1"));
        rawDataQueryMapper.logicalDeleteResult = 1;
        FakeItemQueryMapper itemQueryMapper = new FakeItemQueryMapper();
        itemQueryMapper.findByRawDataIdsResult = List.of(item(101L, "u1", "a1", "u1:a1", "rd-1"));
        RecordingMemoryVector memoryVector = new RecordingMemoryVector();
        RecordingMemory memory = new RecordingMemory();
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1L));
        RawDataDeleteService service =
                new RawDataDeleteService(
                        rawDataQueryMapper, itemQueryMapper, runtimeManager, memoryVector);

        RawDataDeleteResult result = service.deleteRawData(List.of("rd-1"));

        assertThat(result.deletedRawDataCount()).isEqualTo(1);
        assertThat(result.deletedItemCount()).isEqualTo(1);
        assertThat(result.affectedMemoryIds()).containsExactly("u1:a1");
        assertThat(result.insightCleanupRequired()).isTrue();
        assertThat(memory.deletedItemCalls()).containsExactly(Map.entry("u1:a1", List.of(101L)));
        assertThat(memory.invalidatedMemoryIds()).containsExactly("u1:a1");
        assertThat(memoryVector.deletedBatchCalls())
                .containsExactly(Map.entry("u1:a1", List.of("cap-1")));
        assertThat(rawDataQueryMapper.deletedBizIds()).containsExactly("rd-1");
    }

    @Test
    void deleteRawDataFailsWhenVectorDependencyIsUnavailable() {
        FakeRawDataQueryMapper rawDataQueryMapper = new FakeRawDataQueryMapper();
        rawDataQueryMapper.findByBizIdsResult =
                List.of(rawData("rd-1", "u1", "a1", "u1:a1", "cap-1"));
        FakeItemQueryMapper itemQueryMapper = new FakeItemQueryMapper();
        RecordingMemory memory = new RecordingMemory();
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1L));
        RawDataDeleteService service =
                new RawDataDeleteService(rawDataQueryMapper, itemQueryMapper, runtimeManager, null);

        assertThatThrownBy(() -> service.deleteRawData(List.of("rd-1")))
                .isInstanceOf(MemoryRuntimeUnavailableException.class)
                .hasMessageContaining("Memory runtime is unavailable");
    }

    private static AdminRawDataView rawData(
            String rawDataId,
            String userId,
            String agentId,
            String memoryId,
            String captionVectorId) {
        return new AdminRawDataView(
                rawDataId,
                userId,
                agentId,
                memoryId,
                "conversation",
                "api",
                "content-1",
                Map.of("type", "conversation"),
                "caption",
                captionVectorId,
                Map.of(),
                Instant.parse("2026-03-31T10:00:00Z"),
                Instant.parse("2026-03-31T10:01:00Z"),
                Instant.parse("2026-03-31T10:02:00Z"),
                Instant.parse("2026-03-31T10:03:00Z"));
    }

    private static AdminItemView item(
            Long itemId, String userId, String agentId, String memoryId, String rawDataId) {
        return new AdminItemView(
                itemId,
                userId,
                agentId,
                memoryId,
                "likes coffee",
                "user",
                "profile",
                "vec-1",
                rawDataId,
                "hash-1",
                Instant.parse("2026-03-31T10:00:00Z"),
                Instant.parse("2026-03-31T10:00:01Z"),
                Map.of(),
                "FACT",
                "conversation",
                "api",
                Instant.parse("2026-03-31T10:00:02Z"),
                Instant.parse("2026-03-31T10:00:03Z"));
    }

    private static final class FakeRawDataQueryMapper implements AdminRawDataQueryMapper {

        private List<AdminRawDataView> findByBizIdsResult = List.of();
        private int logicalDeleteResult;
        private List<String> deletedBizIds = List.of();

        @Override
        public PageResponse<AdminRawDataView> page(
                com.openmemind.ai.memory.server.domain.rawdata.query.RawDataPageQuery query) {
            return new PageResponse<>(query.pageNo(), query.pageSize(), 0, List.of());
        }

        @Override
        public Optional<AdminRawDataView> findByBizId(String rawDataId) {
            return Optional.empty();
        }

        @Override
        public List<AdminRawDataView> findByBizIds(Collection<String> rawDataIds) {
            return findByBizIdsResult;
        }

        @Override
        public int logicalDeleteByBizIds(Collection<String> rawDataIds) {
            this.deletedBizIds = List.copyOf(rawDataIds);
            return logicalDeleteResult;
        }

        private List<String> deletedBizIds() {
            return deletedBizIds;
        }
    }

    private static final class FakeItemQueryMapper implements AdminItemQueryMapper {

        private List<AdminItemView> findByRawDataIdsResult = List.of();

        @Override
        public PageResponse<AdminItemView> page(ItemPageQuery query) {
            return new PageResponse<>(query.pageNo(), query.pageSize(), 0, List.of());
        }

        @Override
        public Optional<AdminItemView> findByBizId(Long itemId) {
            return Optional.empty();
        }

        @Override
        public List<AdminItemView> findByBizIds(Collection<Long> itemIds) {
            return List.of();
        }

        @Override
        public List<AdminItemView> findByRawDataIds(Collection<String> rawDataIds) {
            return findByRawDataIdsResult;
        }
    }

    private static final class RecordingMemoryVector implements MemoryVector {

        private final List<Map.Entry<String, List<String>>> deletedBatchCalls = new ArrayList<>();

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            return unsupportedMono();
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            return unsupportedMono();
        }

        @Override
        public Mono<Void> delete(MemoryId memoryId, String vectorId) {
            return unsupportedMono();
        }

        @Override
        public Mono<Void> deleteBatch(MemoryId memoryId, List<String> vectorIds) {
            deletedBatchCalls.add(Map.entry(memoryId.toIdentifier(), List.copyOf(vectorIds)));
            return Mono.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK) {
            return unsupportedFlux();
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return unsupportedFlux();
        }

        @Override
        public Mono<List<Float>> embed(String text) {
            return unsupportedMono();
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return unsupportedMono();
        }

        private List<Map.Entry<String, List<String>>> deletedBatchCalls() {
            return deletedBatchCalls;
        }

        private static <T> Mono<T> unsupportedMono() {
            return Mono.error(new UnsupportedOperationException("not implemented in test"));
        }

        private static <T> Flux<T> unsupportedFlux() {
            return Flux.error(new UnsupportedOperationException("not implemented in test"));
        }
    }

    private static final class RecordingMemory implements Memory {

        private final List<Map.Entry<String, List<Long>>> deletedItemCalls = new ArrayList<>();
        private final List<String> invalidatedMemoryIds = new ArrayList<>();

        @Override
        public Mono<Void> deleteItems(MemoryId memoryId, Collection<Long> itemIds) {
            deletedItemCalls.add(Map.entry(memoryId.toIdentifier(), List.copyOf(itemIds)));
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteInsights(MemoryId memoryId, Collection<Long> insightIds) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> invalidate(MemoryId memoryId) {
            invalidatedMemoryIds.add(memoryId.toIdentifier());
            return Mono.empty();
        }

        private List<Map.Entry<String, List<Long>>> deletedItemCalls() {
            return deletedItemCalls;
        }

        private List<String> invalidatedMemoryIds() {
            return invalidatedMemoryIds;
        }

        @Override
        public Mono<ExtractionResult> extract(MemoryId memoryId, RawContent content) {
            return unsupported();
        }

        @Override
        public Mono<ExtractionResult> extract(
                MemoryId memoryId, RawContent content, ExtractionConfig config) {
            return unsupported();
        }

        @Override
        public Mono<ExtractionResult> addMessages(MemoryId memoryId, List<Message> messages) {
            return unsupported();
        }

        @Override
        public Mono<ExtractionResult> addMessages(
                MemoryId memoryId, List<Message> messages, ExtractionConfig config) {
            return unsupported();
        }

        @Override
        public Mono<ExtractionResult> addMessage(MemoryId memoryId, Message message) {
            return unsupported();
        }

        @Override
        public Mono<ExtractionResult> addMessage(
                MemoryId memoryId, Message message, ExtractionConfig config) {
            return unsupported();
        }

        @Override
        public Mono<ContextWindow> getContext(ContextRequest request) {
            return unsupported();
        }

        @Override
        public Mono<ExtractionResult> commit(MemoryId memoryId) {
            return unsupported();
        }

        @Override
        public Mono<ExtractionResult> commit(MemoryId memoryId, ExtractionConfig config) {
            return unsupported();
        }

        @Override
        public Mono<RetrievalResult> retrieve(
                MemoryId memoryId, String query, RetrievalConfig.Strategy strategy) {
            return unsupported();
        }

        @Override
        public Mono<RetrievalResult> retrieve(RetrievalRequest request) {
            return unsupported();
        }

        @Override
        public void close() {}

        private static <T> Mono<T> unsupported() {
            return Mono.error(new UnsupportedOperationException("not implemented in test"));
        }
    }
}
