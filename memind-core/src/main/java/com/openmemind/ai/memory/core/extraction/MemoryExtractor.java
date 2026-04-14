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
package com.openmemind.ai.memory.core.extraction;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import reactor.core.publisher.Mono;

/**
 * Memory extraction pipeline interface.
 *
 * <p>Defines the memory extraction contract: batch extraction via {@link #extract(ExtractionRequest)}
 * and context single-message extraction via {@link #addMessage(MemoryId, Message, ExtractionConfig)}.
 *
 * <p>The primary implementation is {@link DefaultMemoryExtractor}. Decorators (e.g., tracing) wrap this
 * interface to add cross-cutting concerns.
 */
public interface MemoryExtractor {

    /**
     * Execute the complete memory extraction process (batch mode).
     *
     * @param request extraction request containing content, config, and metadata
     * @return extraction result
     */
    Mono<ExtractionResult> extract(ExtractionRequest request);

    /**
     * Process a single message in context mode.
     *
     * <p>Messages are buffered internally. When boundary detection triggers sealing,
     * the buffered messages are extracted as a conversation segment.
     *
     * @param memoryId memory identifier (used as buffer key)
     * @param message single message to process
     * @param config extraction configuration
     * @return extraction result when sealing is triggered; empty Mono when buffered without extraction
     */
    Mono<ExtractionResult> addMessage(MemoryId memoryId, Message message, ExtractionConfig config);
}
