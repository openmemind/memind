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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize;

import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import java.util.Locale;
import java.util.Optional;

/**
 * Builds deterministic entity keys from already-normalized extraction inputs.
 */
public final class EntityKeyCanonicalizer {

    public String canonicalize(
            GraphEntityType type, String normalizedName, Optional<String> reservedSpecialKey) {
        if (reservedSpecialKey != null && reservedSpecialKey.isPresent()) {
            return reservedSpecialKey.get();
        }
        if (normalizedName == null || normalizedName.isBlank()) {
            return "";
        }
        return type.name().toLowerCase(Locale.ROOT) + ":" + normalizedName;
    }
}
