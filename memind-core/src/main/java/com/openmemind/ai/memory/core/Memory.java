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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.ToolCallStats;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * The primary entry point for all memind memory operations.
 *
 * <p>Combines extraction and retrieval into a single unified API:
 *
 * <ul>
 *   <li><b>Extraction</b> — feed conversation messages or tool call records into memory
 *   <li><b>Retrieval</b> — query memory with natural language
 *   <li><b>Tool stats</b> — read aggregated statistics about Agent tool usage
 * </ul>
 *
 * <p>All methods return {@link Mono} and are non-blocking.
 */
public interface Memory {

    // ===== Extraction =====

    /**
     * Extract memories from arbitrary raw content.
     *
     * <p>This is the generic entry point for custom content types.
     * Use {@link #addMessages} for conversations and {@link #reportToolCalls} for tool calls
     * as convenient alternatives.
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
     * Feeds a single message into the streaming extraction pipeline (boundary-detection mode).
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

    // ===== Agent tool stats =====

    /**
     * Reports a single Agent tool call record into memory.
     *
     * <p>Tool call records are processed to extract tool usage patterns, success rates, and
     * parameter statistics that the Agent can later retrieve to improve its behaviour.
     *
     * @param memoryId identifies the Agent whose tool memory to update
     * @param record   the tool call record to persist
     * @return an {@link ExtractionResult} describing what was stored
     */
    Mono<ExtractionResult> reportToolCall(MemoryId memoryId, ToolCallRecord record);

    /**
     * Reports multiple Agent tool call records into memory in a single batch.
     *
     * @param memoryId identifies the Agent whose tool memory to update
     * @param records  the list of tool call records to persist
     * @return an {@link ExtractionResult} describing what was stored
     */
    Mono<ExtractionResult> reportToolCalls(MemoryId memoryId, List<ToolCallRecord> records);

    /**
     * Returns aggregated usage statistics for a specific tool.
     *
     * <p>Statistics include call count, success/failure rates, average latency, and common
     * parameter patterns — useful for the Agent to decide how and when to invoke the tool.
     *
     * @param memoryId identifies the Agent
     * @param toolName the exact name of the tool to query
     * @return a {@link ToolCallStats} snapshot, or empty if no data exists for this tool
     */
    Mono<ToolCallStats> getToolStats(MemoryId memoryId, String toolName);

    /**
     * Returns aggregated usage statistics for every tool the Agent has ever called.
     *
     * <p>The returned map is keyed by tool name. An empty map is returned if no tool calls have
     * been recorded yet.
     *
     * @param memoryId identifies the Agent
     * @return a map of tool name → {@link ToolCallStats}
     */
    Mono<Map<String, ToolCallStats>> getAllToolStats(MemoryId memoryId);
}
