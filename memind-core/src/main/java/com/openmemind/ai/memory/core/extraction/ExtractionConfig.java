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
