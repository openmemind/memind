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
package com.openmemind.ai.memory.server.service.memorythread;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.memorythread.query.MemoryThreadPageQuery;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadItemView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadView;
import com.openmemind.ai.memory.server.mapper.memorythread.AdminMemoryThreadQueryMapper;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import com.openmemind.ai.memory.server.runtime.RuntimeHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class MemoryThreadRebuildServiceTest {

    @Test
    void rebuildMemoryDelegatesToRuntimeMemory() {
        FakeMemoryThreadQueryMapper queryMapper = new FakeMemoryThreadQueryMapper();
        RecordingMemory memory = new RecordingMemory();
        MemoryThreadRebuildService service =
                new MemoryThreadRebuildService(
                        queryMapper,
                        new MemoryRuntimeManager(
                                new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1L)));

        int rebuilt = service.rebuildMemory("u1:a1");

        assertThat(rebuilt).isEqualTo(1);
        assertThat(memory.rebuildCalls()).containsExactly("u1:a1");
    }

    @Test
    void rebuildAllDelegatesForEachKnownMemoryId() {
        FakeMemoryThreadQueryMapper queryMapper = new FakeMemoryThreadQueryMapper();
        queryMapper.memoryIds = List.of("u1:a1", "u2:a1");
        RecordingMemory memory = new RecordingMemory();
        MemoryThreadRebuildService service =
                new MemoryThreadRebuildService(
                        queryMapper,
                        new MemoryRuntimeManager(
                                new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1L)));

        int rebuilt = service.rebuildAll();

        assertThat(rebuilt).isEqualTo(2);
        assertThat(memory.rebuildCalls()).containsExactly("u1:a1", "u2:a1");
    }

    private static final class FakeMemoryThreadQueryMapper implements AdminMemoryThreadQueryMapper {

        private List<String> memoryIds = List.of();

        @Override
        public PageResponse<AdminMemoryThreadView> page(MemoryThreadPageQuery query) {
            return new PageResponse<>(query.pageNo(), query.pageSize(), 0, List.of());
        }

        @Override
        public Optional<AdminMemoryThreadView> findByBizId(Long threadId) {
            return Optional.empty();
        }

        @Override
        public List<AdminMemoryThreadItemView> findItemsByThreadId(Long threadId) {
            return List.of();
        }

        @Override
        public Optional<AdminMemoryThreadItemView> findByItemId(Long itemId) {
            return Optional.empty();
        }

        @Override
        public List<String> listDistinctMemoryIds() {
            return memoryIds;
        }
    }

    private static final class RecordingMemory implements Memory {

        private final List<String> rebuildCalls = new ArrayList<>();

        @Override
        public void rebuildMemoryThreads(MemoryId memoryId) {
            rebuildCalls.add(memoryId.toIdentifier());
        }

        private List<String> rebuildCalls() {
            return rebuildCalls;
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
        public Mono<Void> deleteItems(MemoryId memoryId, Collection<Long> itemIds) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteInsights(MemoryId memoryId, Collection<Long> insightIds) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> invalidate(MemoryId memoryId) {
            return Mono.empty();
        }

        @Override
        public void close() {}

        private static <T> Mono<T> unsupported() {
            return Mono.error(new UnsupportedOperationException("not implemented in test"));
        }
    }
}
