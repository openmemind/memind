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
package com.openmemind.ai.memory.core.extraction.item.dedup;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Memory item deduplication strategy interface
 *
 */
public interface MemoryItemDeduplicator {

    /**
     * Deduplicate the extracted memory entries
     *
     * @param memoryId Memory identifier
     * @param entries Entries to be deduplicated
     * @return Deduplication result
     */
    Mono<DeduplicationResult> deduplicate(MemoryId memoryId, List<ExtractedMemoryEntry> entries);

    /**
     * The span name corresponding to this deduplicator
     */
    String spanName();
}
