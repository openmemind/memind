package com.openmemind.ai.memory.core.extraction.item;

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
 */
public record ItemExtractionConfig(
        MemoryScope scope, String contentType, boolean enableForesight, String language) {

    public static ItemExtractionConfig defaults() {
        return new ItemExtractionConfig(
                MemoryScope.USER, ContentTypes.CONVERSATION, false, PromptResult.DEFAULT_LANGUAGE);
    }

    public static ItemExtractionConfig from(ExtractionConfig config, String contentType) {
        return new ItemExtractionConfig(
                config.scope(), contentType, config.enableForesight(), config.language());
    }
}
