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
import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.builder.PromptBudgetOptions;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.prompt.PromptResult;
import java.util.Set;

/**
 * MemoryItem extraction layer configuration
 *
 * @param scope memory scope
 * @param contentType content type identifier (e.g. {@link ConversationContent#TYPE})
 * @param enableForesight whether to enable Foresight
 * @param language target output language
 * @param promptBudget prompt budget governance for item extraction
 * @param graph graph extraction/materialization runtime options
 */
public record ItemExtractionConfig(
        MemoryScope scope,
        String contentType,
        Set<MemoryCategory> allowedCategories,
        boolean enableForesight,
        String language,
        PromptBudgetOptions promptBudget,
        ItemGraphOptions graph) {

    public ItemExtractionConfig(
            MemoryScope scope, String contentType, boolean enableForesight, String language) {
        this(
                scope,
                contentType,
                MemoryCategory.userCategories(),
                enableForesight,
                language,
                PromptBudgetOptions.defaults(),
                ItemGraphOptions.defaults());
    }

    public ItemExtractionConfig(
            MemoryScope scope,
            String contentType,
            Set<MemoryCategory> allowedCategories,
            boolean enableForesight,
            String language) {
        this(
                scope,
                contentType,
                allowedCategories,
                enableForesight,
                language,
                PromptBudgetOptions.defaults(),
                ItemGraphOptions.defaults());
    }

    public ItemExtractionConfig(
            MemoryScope scope,
            String contentType,
            boolean enableForesight,
            String language,
            PromptBudgetOptions promptBudget) {
        this(
                scope,
                contentType,
                MemoryCategory.userCategories(),
                enableForesight,
                language,
                promptBudget,
                ItemGraphOptions.defaults());
    }

    public ItemExtractionConfig(
            MemoryScope scope,
            String contentType,
            Set<MemoryCategory> allowedCategories,
            boolean enableForesight,
            String language,
            PromptBudgetOptions promptBudget) {
        this(
                scope,
                contentType,
                allowedCategories,
                enableForesight,
                language,
                promptBudget,
                ItemGraphOptions.defaults());
    }

    public static ItemExtractionConfig defaults() {
        return new ItemExtractionConfig(
                MemoryScope.USER,
                ConversationContent.TYPE,
                MemoryCategory.userCategories(),
                false,
                PromptResult.DEFAULT_LANGUAGE,
                PromptBudgetOptions.defaults(),
                ItemGraphOptions.defaults());
    }

    public static ItemExtractionConfig from(
            ExtractionConfig config,
            String contentType,
            ItemExtractionOptions options,
            Set<MemoryCategory> allowedCategories) {
        return new ItemExtractionConfig(
                config.scope(),
                contentType,
                allowedCategories,
                config.enableForesight(),
                config.language(),
                options.promptBudget(),
                options.graph());
    }
}
