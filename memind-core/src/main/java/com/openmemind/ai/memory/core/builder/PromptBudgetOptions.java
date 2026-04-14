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
package com.openmemind.ai.memory.core.builder;

/**
 * Prompt budget reserved around item extraction requests.
 */
public record PromptBudgetOptions(
        int maxInputTokens,
        int reservedPromptOverhead,
        int reservedOutputTokens,
        int safetyMargin) {

    public PromptBudgetOptions {
        if (maxInputTokens <= 0) {
            throw new IllegalArgumentException("maxInputTokens must be positive");
        }
        if (reservedPromptOverhead < 0 || reservedOutputTokens < 0 || safetyMargin < 0) {
            throw new IllegalArgumentException("reserved token budgets must be non-negative");
        }
        if (maxInputTokens <= reservedPromptOverhead + reservedOutputTokens + safetyMargin) {
            throw new IllegalArgumentException("maxInputTokens must exceed reserved token budgets");
        }
    }

    public static PromptBudgetOptions defaults() {
        return new PromptBudgetOptions(8192, 1400, 1200, 400);
    }
}
