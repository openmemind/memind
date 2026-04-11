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
package com.openmemind.ai.memory.core.extraction.item;

import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.PromptBudgetOptions;
import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.prompt.PromptResult;

/**
 * MemoryItem extraction layer configuration
 *
 * @param scope memory scope
 * @param contentType content type identifier (e.g. ContentTypes.CONVERSATION)
 * @param enableForesight whether to enable Foresight
 * @param language target output language
 * @param promptBudget prompt budget governance for item extraction
 */
public record ItemExtractionConfig(
        MemoryScope scope,
        String contentType,
        boolean enableForesight,
        String language,
        PromptBudgetOptions promptBudget) {

    public ItemExtractionConfig(
            MemoryScope scope, String contentType, boolean enableForesight, String language) {
        this(scope, contentType, enableForesight, language, PromptBudgetOptions.defaults());
    }

    public static ItemExtractionConfig defaults() {
        return new ItemExtractionConfig(
                MemoryScope.USER,
                ContentTypes.CONVERSATION,
                false,
                PromptResult.DEFAULT_LANGUAGE,
                PromptBudgetOptions.defaults());
    }

    public static ItemExtractionConfig from(
            ExtractionConfig config, String contentType, ItemExtractionOptions options) {
        return new ItemExtractionConfig(
                config.scope(),
                contentType,
                config.enableForesight(),
                config.language(),
                options.promptBudget());
    }
}
