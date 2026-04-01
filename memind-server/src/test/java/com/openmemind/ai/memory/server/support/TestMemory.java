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
package com.openmemind.ai.memory.server.support;

import com.openmemind.ai.memory.core.Memory;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class TestMemory implements Memory {

    private final Runnable onClose;

    public TestMemory() {
        this(() -> {});
    }

    public TestMemory(Runnable onClose) {
        this.onClose = Objects.requireNonNull(onClose, "onClose");
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
        return unsupported();
    }

    @Override
    public Mono<Void> deleteInsights(MemoryId memoryId, Collection<Long> insightIds) {
        return unsupported();
    }

    @Override
    public Mono<ExtractionResult> reportToolCall(MemoryId memoryId, ToolCallRecord record) {
        return unsupported();
    }

    @Override
    public Mono<ExtractionResult> reportToolCalls(MemoryId memoryId, List<ToolCallRecord> records) {
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
    public void close() {
        onClose.run();
    }

    private static <T> Mono<T> unsupported() {
        return Mono.error(
                new UnsupportedOperationException("TestMemory does not implement this call"));
    }
}
