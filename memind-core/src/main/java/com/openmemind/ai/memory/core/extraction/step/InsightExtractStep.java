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
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import reactor.core.publisher.Mono;

/**
 * Insight extraction step
 *
 * <p>Responsible for generating insights (such as classification summaries, user profiles, etc.) from MemoryItem
 *
 */
public interface InsightExtractStep {

    /**
     * Extract Insight
     *
     * @param memoryId Memory identifier
     * @param memoryItemResult MemoryItem extraction result
     * @return Generated result
     */
    Mono<InsightResult> extract(MemoryId memoryId, MemoryItemResult memoryItemResult);

    /**
     * Extract Insight (with language hint)
     *
     * @param memoryId Memory identifier
     * @param memoryItemResult MemoryItem extraction result
     * @param language Output language hint, can be null
     * @return Generated result
     */
    default Mono<InsightResult> extract(
            MemoryId memoryId, MemoryItemResult memoryItemResult, String language) {
        return extract(memoryId, memoryItemResult);
    }
}
