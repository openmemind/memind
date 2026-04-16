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

public record ItemGraphOptions(
        boolean enabled,
        int maxEntitiesPerItem,
        int maxCausalReferencesPerItem,
        int maxTemporalLinksPerItem,
        int maxSemanticLinksPerItem,
        double semanticMinScore) {

    public ItemGraphOptions {
        if (maxEntitiesPerItem <= 0) {
            throw new IllegalArgumentException("maxEntitiesPerItem must be positive");
        }
        if (maxCausalReferencesPerItem <= 0) {
            throw new IllegalArgumentException("maxCausalReferencesPerItem must be positive");
        }
        if (maxTemporalLinksPerItem <= 0) {
            throw new IllegalArgumentException("maxTemporalLinksPerItem must be positive");
        }
        if (maxSemanticLinksPerItem <= 0) {
            throw new IllegalArgumentException("maxSemanticLinksPerItem must be positive");
        }
        if (semanticMinScore < 0.0d || semanticMinScore > 1.0d) {
            throw new IllegalArgumentException("semanticMinScore must be in [0,1]");
        }
    }

    public static ItemGraphOptions defaults() {
        return new ItemGraphOptions(false, 8, 2, 10, 5, 0.82d);
    }

    public ItemGraphOptions withEnabled(boolean enabled) {
        return new ItemGraphOptions(
                enabled,
                maxEntitiesPerItem,
                maxCausalReferencesPerItem,
                maxTemporalLinksPerItem,
                maxSemanticLinksPerItem,
                semanticMinScore);
    }
}
