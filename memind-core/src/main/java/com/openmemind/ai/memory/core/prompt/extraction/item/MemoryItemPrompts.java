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
package com.openmemind.ai.memory.core.prompt.extraction.item;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Memory item extraction Prompt construction -- Unique entry (dispatcher)
 *
 * <p>Delegates to {@link MemoryItemUnifiedPrompts} for unified mode.
 */
public final class MemoryItemPrompts {

    private MemoryItemPrompts() {}

    /**
     * Build prompt for Unified mode
     */
    public static PromptTemplate buildUnified(
            List<MemoryInsightType> insightTypes, String segmentText) {
        return MemoryItemUnifiedPrompts.build(insightTypes, segmentText, null, null, null);
    }

    /**
     * Build prompt for Unified mode
     */
    public static PromptTemplate buildUnified(
            List<MemoryInsightType> insightTypes, String segmentText, Instant referenceTime) {
        return MemoryItemUnifiedPrompts.build(insightTypes, segmentText, referenceTime, null, null);
    }

    /**
     * Build prompt for Unified mode (with userName)
     *
     * @param insightTypes  list of insight types
     * @param segmentText   text to be extracted
     * @param referenceTime conversation reference time
     * @param userName      user name (replaces "User" in prompt when not empty)
     * @return prompt template
     */
    public static PromptTemplate buildUnified(
            List<MemoryInsightType> insightTypes,
            String segmentText,
            Instant referenceTime,
            String userName) {
        return MemoryItemUnifiedPrompts.build(
                insightTypes, segmentText, referenceTime, userName, null);
    }

    /**
     * Build prompt for Unified mode (with category filtering)
     *
     * @param insightTypes  list of insight types
     * @param segmentText   text to be extracted
     * @param referenceTime conversation reference time
     * @param userName      user name (replaces "User" in prompt when not empty)
     * @param categories    categories to include in the prompt
     * @return prompt template
     */
    public static PromptTemplate buildUnified(
            List<MemoryInsightType> insightTypes,
            String segmentText,
            Instant referenceTime,
            String userName,
            Set<MemoryCategory> categories) {
        return MemoryItemUnifiedPrompts.build(
                insightTypes, segmentText, referenceTime, userName, categories);
    }
}
