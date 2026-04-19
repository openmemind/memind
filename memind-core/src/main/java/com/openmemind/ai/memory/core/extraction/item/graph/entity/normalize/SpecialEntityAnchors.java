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

import java.util.Map;
import java.util.Optional;

/**
 * Shared exact-path contract for reserved conversational role anchors.
 */
final class SpecialEntityAnchors {

    private static final Map<String, String> RESERVED_KEY_BY_NAME =
            Map.ofEntries(
                    Map.entry("self", "special:self"),
                    Map.entry("我", "special:self"),
                    Map.entry("本人", "special:self"),
                    Map.entry("user", "special:user"),
                    Map.entry("用户", "special:user"),
                    Map.entry("assistant", "special:assistant"),
                    Map.entry("助手", "special:assistant"));

    static Optional<String> reservedKeyFor(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(RESERVED_KEY_BY_NAME.get(normalizedName));
    }

    static boolean isReservedAnchorName(String normalizedName) {
        return reservedKeyFor(normalizedName).isPresent();
    }

    private SpecialEntityAnchors() {}
}
