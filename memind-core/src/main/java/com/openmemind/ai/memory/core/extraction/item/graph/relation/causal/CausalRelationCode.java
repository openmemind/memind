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
package com.openmemind.ai.memory.core.extraction.item.graph.relation.causal;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Canonical causal relation subtypes.
 */
public enum CausalRelationCode {
    CAUSED_BY("caused_by"),
    ENABLED_BY("enabled_by"),
    MOTIVATED_BY("motivated_by");

    private final String code;

    CausalRelationCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static CausalRelationCode fromCode(String code) {
        String normalized = normalize(code);
        return Arrays.stream(values())
                .filter(value -> value.code.equals(normalized))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "unsupported causal relation code: " + code));
    }

    private static String normalize(String code) {
        Objects.requireNonNull(code, "code");
        return code.trim().toLowerCase(Locale.ROOT);
    }
}
