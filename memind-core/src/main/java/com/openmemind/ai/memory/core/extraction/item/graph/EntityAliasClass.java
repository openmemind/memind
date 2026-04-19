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
package com.openmemind.ai.memory.core.extraction.item.graph;

import java.util.Arrays;
import java.util.Optional;

/**
 * Frozen alias-class vocabulary for Stage 2.
 */
public enum EntityAliasClass {
    CASE_ONLY("case_only"),
    PUNCTUATION("punctuation"),
    SPACING("spacing"),
    ORG_SUFFIX("org_suffix"),
    EXPLICIT_PARENTHETICAL("explicit_parenthetical"),
    EXPLICIT_SLASH_APPOSITION("explicit_slash_apposition"),
    USER_DICTIONARY("user_dictionary");

    private final String wireValue;

    EntityAliasClass(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static Optional<EntityAliasClass> fromWireValue(String wireValue) {
        return Arrays.stream(values())
                .filter(value -> value.wireValue.equals(wireValue))
                .findFirst();
    }
}
