package com.openmemind.ai.memory.core.extraction;

import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.prompt.PromptResult;
import java.time.Duration;

/**
 * Memory extraction configuration
 *
 * @param enableInsight Whether to enable Insight generation
 * @param scope The scope of the extracted memory (USER or AGENT)
 * @param enableForesight Whether to enable Foresight predictive extraction
 * @param timeout Overall timeout duration
 * @param language Target output language for extraction (e.g. "English", "Chinese")
 */
public record ExtractionConfig(
        boolean enableInsight,
        MemoryScope scope,
        boolean enableForesight,
        Duration timeout,
        String language) {

    /** Default configuration */
    public static ExtractionConfig defaults() {
        return new ExtractionConfig(
                true,
                MemoryScope.USER,
                false,
                Duration.ofMinutes(10),
                PromptResult.DEFAULT_LANGUAGE);
    }

    /** Configuration without Insight generation */
    public static ExtractionConfig withoutInsight() {
        return new ExtractionConfig(
                false,
                MemoryScope.USER,
                false,
                Duration.ofMinutes(10),
                PromptResult.DEFAULT_LANGUAGE);
    }

    /** Configuration for Agent memory only */
    public static ExtractionConfig agentOnly() {
        return new ExtractionConfig(
                true,
                MemoryScope.AGENT,
                false,
                Duration.ofMinutes(10),
                PromptResult.DEFAULT_LANGUAGE);
    }

    public ExtractionConfig withEnableInsight(boolean enable) {
        return new ExtractionConfig(enable, scope, enableForesight, timeout, language);
    }

    public ExtractionConfig withScope(MemoryScope scope) {
        return new ExtractionConfig(enableInsight, scope, enableForesight, timeout, language);
    }

    public ExtractionConfig withTimeout(Duration timeout) {
        return new ExtractionConfig(enableInsight, scope, enableForesight, timeout, language);
    }

    public ExtractionConfig withEnableForesight(boolean enable) {
        return new ExtractionConfig(enableInsight, scope, enable, timeout, language);
    }

    public ExtractionConfig withLanguage(String language) {
        return new ExtractionConfig(enableInsight, scope, enableForesight, timeout, language);
    }
}
