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
package com.openmemind.ai.memory.core.extraction.step;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import reactor.core.publisher.Mono;

/**
 * MemoryItem extraction step
 *
 * <p>Responsible for extracting memory items from RawData segments, deduplication, and persistence
 *
 */
public interface MemoryItemExtractStep {

    /**
     * Extract MemoryItem
     *
     * @param memoryId Memory identifier
     * @param rawDataResult RawData processing result
     * @param config Item extraction layer configuration
     * @return Extraction result
     */
    Mono<MemoryItemResult> extract(
            MemoryId memoryId, RawDataResult rawDataResult, ItemExtractionConfig config);
}
