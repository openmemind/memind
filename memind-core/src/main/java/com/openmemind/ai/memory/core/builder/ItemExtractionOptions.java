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

import java.util.Objects;

public record ItemExtractionOptions(
        boolean foresightEnabled, PromptBudgetOptions promptBudget, ItemGraphOptions graph) {

    public ItemExtractionOptions {
        promptBudget = Objects.requireNonNull(promptBudget, "promptBudget");
        graph = Objects.requireNonNull(graph, "graph");
    }

    public ItemExtractionOptions(boolean foresightEnabled) {
        this(foresightEnabled, PromptBudgetOptions.defaults(), ItemGraphOptions.defaults());
    }

    public ItemExtractionOptions(boolean foresightEnabled, PromptBudgetOptions promptBudget) {
        this(foresightEnabled, promptBudget, ItemGraphOptions.defaults());
    }

    public static ItemExtractionOptions defaults() {
        return new ItemExtractionOptions(
                false, PromptBudgetOptions.defaults(), ItemGraphOptions.defaults());
    }
}
