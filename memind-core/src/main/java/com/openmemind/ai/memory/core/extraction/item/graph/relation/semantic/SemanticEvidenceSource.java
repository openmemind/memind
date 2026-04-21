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
package com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Canonical semantic evidence channels ordered by persistence precedence.
 */
public enum SemanticEvidenceSource {
    VECTOR_SEARCH("vector_search", 3),
    SAME_BATCH_VECTOR("same_batch_vector", 2),
    VECTOR_SEARCH_FALLBACK("vector_search_fallback", 1);

    private final String code;
    private final int precedence;

    SemanticEvidenceSource(String code, int precedence) {
        this.code = code;
        this.precedence = precedence;
    }

    public String code() {
        return code;
    }

    public int precedence() {
        return precedence;
    }

    public static SemanticEvidenceSource fromCode(String code) {
        String normalized = normalize(code);
        return Arrays.stream(values())
                .filter(value -> value.code.equals(normalized))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "unsupported semantic evidence source: " + code));
    }

    private static String normalize(String code) {
        Objects.requireNonNull(code, "code");
        return code.trim().toLowerCase(Locale.ROOT);
    }
}
