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
package com.openmemind.ai.memory.server.service.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.ToolCallStats;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.context.ContextRequest;
import com.openmemind.ai.memory.core.extraction.context.ContextWindow;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.mapper.item.AdminItemQueryMapper;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import com.openmemind.ai.memory.server.runtime.RuntimeHandle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ItemDeleteServiceTest {

    @Test
    void deleteItemsRoutesThroughRuntimeMemoryByBusinessId() {
        FakeItemQueryMapper itemQueryMapper = new FakeItemQueryMapper();
        itemQueryMapper.findByBizIdsResult =
                List.of(
                        item(101L, "u1", "a1", "u1:a1", "rd-1"),
                        item(102L, "u1", "a1", "u1:a1", "rd-2"));
        RecordingMemory memory = new RecordingMemory();
        ItemDeleteService service =
                new ItemDeleteService(
                        itemQueryMapper,
                        new MemoryRuntimeManager(
                                new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1L)));

        BatchDeleteResult result = service.deleteItems(List.of(101L, 102L));

        assertThat(result.deletedCount()).isEqualTo(2);
        assertThat(result.affectedMemoryIds()).containsExactly("u1:a1");
        assertThat(memory.deletedItemCalls())
                .containsExactly(Map.entry("u1:a1", List.of(101L, 102L)));
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
                Instant.parse("2026-03-31T10:00:02Z"),
                Instant.parse("2026-03-31T10:00:03Z"));
    }

    private static final class FakeItemQueryMapper implements AdminItemQueryMapper {

        private List<AdminItemView> findByBizIdsResult = List.of();

        @Override
        public PageResponse<AdminItemView> page(
                com.openmemind.ai.memory.server.domain.item.query.ItemPageQuery query) {
            return new PageResponse<>(query.pageNo(), query.pageSize(), 0, List.of());
        }

        @Override
        public Optional<AdminItemView> findByBizId(Long itemId) {
            return Optional.empty();
        }

        @Override
        public List<AdminItemView> findByBizIds(Collection<Long> itemIds) {
            return findByBizIdsResult;
        }

        @Override
        public List<AdminItemView> findByRawDataIds(Collection<String> rawDataIds) {
            return List.of();
        }
    }

    private static final class RecordingMemory implements Memory {

        private final List<Map.Entry<String, List<Long>>> deletedItemCalls = new ArrayList<>();

        @Override
        public Mono<Void> deleteItems(MemoryId memoryId, Collection<Long> itemIds) {
            deletedItemCalls.add(Map.entry(memoryId.toIdentifier(), List.copyOf(itemIds)));
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteInsights(MemoryId memoryId, Collection<Long> insightIds) {
            return Mono.empty();
        }

        private List<Map.Entry<String, List<Long>>> deletedItemCalls() {
            return deletedItemCalls;
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
        public Mono<Void> invalidate(MemoryId memoryId) {
            return Mono.empty();
        }

        @Override
        public Mono<ExtractionResult> reportToolCall(MemoryId memoryId, ToolCallRecord record) {
            return unsupported();
        }

        @Override
        public Mono<ExtractionResult> reportToolCalls(
                MemoryId memoryId, List<ToolCallRecord> records) {
            return unsupported();
        }

        @Override
        public Mono<ToolCallStats> getToolStats(MemoryId memoryId, String toolName) {
            return unsupported();
        }

        @Override
        public Mono<Map<String, ToolCallStats>> getAllToolStats(MemoryId memoryId) {
            return unsupported();
        }

        @Override
        public void close() {}

        private static <T> Mono<T> unsupported() {
            return Mono.error(new UnsupportedOperationException("not implemented in test"));
        }
    }
}
