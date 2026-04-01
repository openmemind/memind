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
package com.openmemind.ai.memory.server.service.insight;

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
import com.openmemind.ai.memory.server.domain.insight.view.AdminInsightView;
import com.openmemind.ai.memory.server.mapper.insight.AdminInsightQueryMapper;
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

class InsightDeleteServiceTest {

    @Test
    void deleteInsightsRoutesThroughRuntimeMemoryByBusinessId() {
        FakeInsightQueryMapper insightQueryMapper = new FakeInsightQueryMapper();
        insightQueryMapper.findByBizIdsResult = List.of(insight(201L, "u1", "a1", "u1:a1"));
        RecordingMemory memory = new RecordingMemory();
        InsightDeleteService service =
                new InsightDeleteService(
                        insightQueryMapper,
                        new MemoryRuntimeManager(
                                new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1L)));

        BatchDeleteResult result = service.deleteInsights(List.of(201L));

        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.affectedMemoryIds()).containsExactly("u1:a1");
        assertThat(memory.deletedInsightCalls()).containsExactly(Map.entry("u1:a1", List.of(201L)));
    }

    private static AdminInsightView insight(
            Long insightId, String userId, String agentId, String memoryId) {
        return new AdminInsightView(
                insightId,
                userId,
                agentId,
                memoryId,
                "profile",
                "user",
                "preference",
                List.of("profile"),
                "prefers concise answers",
                List.of(),
                "group-1",
                0.95F,
                Instant.parse("2026-03-31T10:00:00Z"),
                List.of(),
                "LEAF",
                null,
                List.of(),
                1,
                Instant.parse("2026-03-31T10:00:01Z"),
                Instant.parse("2026-03-31T10:00:02Z"));
    }

    private static final class FakeInsightQueryMapper implements AdminInsightQueryMapper {

        private List<AdminInsightView> findByBizIdsResult = List.of();

        @Override
        public PageResponse<AdminInsightView> page(
                com.openmemind.ai.memory.server.domain.insight.query.InsightPageQuery query) {
            return new PageResponse<>(query.pageNo(), query.pageSize(), 0, List.of());
        }

        @Override
        public Optional<AdminInsightView> findByBizId(Long insightId) {
            return Optional.empty();
        }

        @Override
        public List<AdminInsightView> findByBizIds(Collection<Long> insightIds) {
            return findByBizIdsResult;
        }
    }

    private static final class RecordingMemory implements Memory {

        private final List<Map.Entry<String, List<Long>>> deletedInsightCalls = new ArrayList<>();

        @Override
        public Mono<Void> deleteItems(MemoryId memoryId, Collection<Long> itemIds) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteInsights(MemoryId memoryId, Collection<Long> insightIds) {
            deletedInsightCalls.add(Map.entry(memoryId.toIdentifier(), List.copyOf(insightIds)));
            return Mono.empty();
        }

        private List<Map.Entry<String, List<Long>>> deletedInsightCalls() {
            return deletedInsightCalls;
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
