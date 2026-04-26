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
package com.openmemind.ai.memory.core;

import com.openmemind.ai.memory.core.builder.DefaultMemoryBuilder;
import com.openmemind.ai.memory.core.builder.MemoryBuilder;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryThreadRuntimeStatus;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.context.ContextRequest;
import com.openmemind.ai.memory.core.extraction.context.ContextWindow;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * The primary entry point for all memind memory operations.
 *
 * <p>Combines context management, extraction, and retrieval into a single unified API:
 *
 * <ul>
 *   <li><b>Context</b> — manage conversation context with automatic buffer and commit
 *   <li><b>Extraction</b> — feed conversation messages or arbitrary raw content into memory
 *   <li><b>Retrieval</b> — query memory with natural language
 * </ul>
 *
 * <p>All methods return {@link Mono} and are non-blocking.
 */
public interface Memory extends AutoCloseable {

    static MemoryBuilder builder() {
        return new DefaultMemoryBuilder();
    }

    // ===== Extraction =====

    /**
     * Extract memories using a fully constructed extraction request.
     *
     * <p>Use this overload for parser-backed file ingestion, downloader-backed URL ingestion,
     * or when the caller needs to provide request metadata directly.
     *
     * @param request extraction request including memory id, payload, and config
     * @return extraction result
     */
    default Mono<ExtractionResult> extract(ExtractionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.content() == null) {
            return Mono.error(
                    new UnsupportedOperationException(
                            "This Memory implementation does not support file/url extraction"
                                    + " requests"));
        }
        return extract(request.memoryId(), request.content(), request.config());
    }

    /**
     * Extract memories from arbitrary raw content.
     *
     * <p>This is the generic entry point for custom content types.
     * Use {@link #addMessages} for conversations.
     *
     * @param memoryId the memory identity
     * @param content raw content to extract from
     * @return extraction result
     */
    Mono<ExtractionResult> extract(MemoryId memoryId, RawContent content);

    /**
     * Extract memories from arbitrary raw content with a custom extraction config.
     *
     * @param memoryId the memory identity
     * @param content raw content to extract from
     * @param config custom extraction configuration
     * @return extraction result
     */
    Mono<ExtractionResult> extract(MemoryId memoryId, RawContent content, ExtractionConfig config);

    /**
     * Extracts memory from a batch of conversation messages using the default extraction config.
     *
     * <p>Use this when you have a complete conversation segment ready to process at once.
     *
     * @param memoryId identifies whose memory to write to
     * @param messages the conversation messages to extract from
     * @return an {@link ExtractionResult} describing what was extracted
     */
    Mono<ExtractionResult> addMessages(MemoryId memoryId, List<Message> messages);

    /**
     * Extracts memory from a batch of conversation messages with a custom extraction config.
     *
     * <p>Use {@link ExtractionConfig} to override chunking strategy, insight types, and other
     * extraction behaviour.
     *
     * @param memoryId identifies whose memory to write to
     * @param messages the conversation messages to extract from
     * @param config   custom extraction configuration
     * @return an {@link ExtractionResult} describing what was extracted
     */
    Mono<ExtractionResult> addMessages(
            MemoryId memoryId, List<Message> messages, ExtractionConfig config);

    /**
     * Feeds a single message into the context extraction pipeline (boundary-detection mode).
     *
     * <p>Messages are buffered internally under {@code memoryId}. Extraction is triggered
     * automatically when the boundary detector decides the accumulated buffer is ready.
     *
     * <ul>
     *   <li>When extraction fires — emits an {@link ExtractionResult}.
     *   <li>When the buffer is not yet full — emits an empty signal; use
     *       {@code switchIfEmpty} / {@code defaultIfEmpty} to handle this case.
     * </ul>
     *
     * <p>This is the recommended method for real-time chat applications where messages arrive one
     * at a time.
     *
     * @param memoryId memory identifier used as the buffer key
     * @param message  the incoming message
     * @return an {@link ExtractionResult} if extraction was triggered, otherwise empty
     */
    Mono<ExtractionResult> addMessage(MemoryId memoryId, Message message);

    /**
     * Same as {@link #addMessage(MemoryId, Message)} with a custom extraction config.
     *
     * @param memoryId memory identifier used as the buffer key
     * @param message  the incoming message
     * @param config   custom extraction configuration
     * @return an {@link ExtractionResult} if extraction was triggered, otherwise empty
     */
    Mono<ExtractionResult> addMessage(MemoryId memoryId, Message message, ExtractionConfig config);

    // ===== Context =====

    /**
     * Assembles a context window from the current conversation buffer and optionally
     * retrieved memories.
     *
     * <p>This is the primary method for building LLM context in real-time Agent applications.
     * It reads the messages currently buffered under {@code memoryId}, retrieves relevant
     * memories extracted from previous conversations, and returns a ready-to-use
     * {@link ContextWindow}.
     *
     * @param request context request specifying memory id, token budget, and retrieval options
     * @return a {@link ContextWindow} containing recent messages and retrieved memories
     */
    Mono<ContextWindow> getContext(ContextRequest request);

    /**
     * Manually commits the current conversation buffer, triggering memory extraction immediately.
     *
     * <p>Normally the buffer is committed automatically when the context commit detector
     * decides the accumulated messages are ready. Use this method to force an immediate commit,
     * for example at end-of-session or before the application shuts down.
     *
     * <p>If the buffer is empty, returns an empty extraction result.
     *
     * @param memoryId identifies whose buffer to commit
     * @return an {@link ExtractionResult} describing what was extracted
     */
    Mono<ExtractionResult> commit(MemoryId memoryId);

    /**
     * Manually commits the current conversation buffer with a custom extraction config.
     *
     * @param memoryId identifies whose buffer to commit
     * @param config   custom extraction configuration
     * @return an {@link ExtractionResult} describing what was extracted
     */
    Mono<ExtractionResult> commit(MemoryId memoryId, ExtractionConfig config);

    // ===== Retrieval =====

    /**
     * Retrieves relevant memories for the given query using the specified strategy.
     *
     * <p>Use {@link RetrievalConfig.Strategy#SIMPLE} for low-latency, algorithm-only retrieval.
     * Use {@link RetrievalConfig.Strategy#DEEP} for LLM-assisted query expansion and sufficiency
     * checking. For fine-grained control, use {@link #retrieve(RetrievalRequest)} instead.
     *
     * @param memoryId identifies whose memory to search
     * @param query    natural-language query describing what to look for
     * @param strategy retrieval strategy to use
     * @return a {@link RetrievalResult} containing ranked, relevant memory items
     */
    Mono<RetrievalResult> retrieve(
            MemoryId memoryId, String query, RetrievalConfig.Strategy strategy);

    /**
     * Retrieves relevant memories with full control over scope, categories, and retrieval config.
     *
     * <p>Use the {@link RetrievalRequest} factory methods to build a scoped request:
     *
     * <ul>
     *   <li>{@link RetrievalRequest#userMemory(MemoryId, String, RetrievalConfig.Strategy)} — restrict to user memory
     *   <li>{@link RetrievalRequest#agentMemory(MemoryId, String, RetrievalConfig.Strategy)} — restrict to Agent memory
     *   <li>{@link RetrievalRequest#byCategories(MemoryId, String, java.util.Set, RetrievalConfig.Strategy)} — filter by
     *       specific memory categories
     * </ul>
     *
     * @param request a fully constructed retrieval request
     * @return a {@link RetrievalResult} containing ranked, relevant memory items
     */
    Mono<RetrievalResult> retrieve(RetrievalRequest request);

    // ===== Deletion =====

    /**
     * Deletes only the requested memory items for the given memory id.
     *
     * @param memoryId the memory identity
     * @param itemIds the item ids to delete
     * @return completion signal
     */
    Mono<Void> deleteItems(MemoryId memoryId, Collection<Long> itemIds);

    /**
     * Deletes only the requested insights for the given memory id.
     *
     * @param memoryId the memory identity
     * @param insightIds the insight ids to delete
     * @return completion signal
     */
    Mono<Void> deleteInsights(MemoryId memoryId, Collection<Long> insightIds);

    /**
     * Invalidates retrieval and text-search caches after an out-of-band data change.
     *
     * <p>This is intended for administrative workflows that mutate storage directly rather than
     * going through the standard runtime deletion APIs.
     *
     * @param memoryId the memory identity whose caches should be invalidated
     * @return completion signal
     */
    default Mono<Void> invalidate(MemoryId memoryId) {
        return Mono.empty();
    }

    /**
     * Forces a flush of any buffered insight data for the given memory.
     *
     * <p>Normally insights are built asynchronously when buffers reach a threshold.
     * Call this to force-process all remaining buffered items immediately — useful
     * at end-of-session, application shutdown, or when the caller needs insights
     * to be available right away.
     *
     * <p>The default implementation is a no-op, suitable for configurations
     * where the insight layer is not enabled.
     *
     * @param memoryId identifies whose memory to flush
     */
    default void flushInsights(MemoryId memoryId) {
        flushInsights(memoryId, null);
    }

    /**
     * Forces a flush of any buffered insight data, specifying the language for
     * LLM-generated insight summaries.
     *
     * @param memoryId identifies whose memory to flush
     * @param language the language for insight generation, or {@code null} for the default
     */
    default void flushInsights(MemoryId memoryId, String language) {}

    /**
     * Forces a flush of any queued memory-thread derivation work for the given memory.
     *
     * <p>The default implementation is a no-op when memory-thread support is disabled.
     *
     * @param memoryId identifies whose memory-thread work to flush
     */
    default void flushMemoryThreads(MemoryId memoryId) {}

    /**
     * Rebuilds memory-thread state from persisted items for the given memory.
     *
     * <p>The default implementation is a no-op when memory-thread support is disabled.
     *
     * @param memoryId identifies whose memory-thread state to rebuild
     */
    default void rebuildMemoryThreads(MemoryId memoryId) {}

    /**
     * Returns the current runtime status of thread projection materialization for one memory.
     */
    default MemoryThreadRuntimeStatus getThreadRuntimeStatus(MemoryId memoryId) {
        return MemoryThreadRuntimeStatus.disabled("memoryThread disabled");
    }

    /**
     * Releases runtime-owned resources created for this memory instance.
     */
    @Override
    void close();
}
