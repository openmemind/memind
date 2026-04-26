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

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Exact-path entity name normalizer with conservative multilingual-safe behavior.
 */
public final class LanguageAwareEntityNameNormalizer implements EntityNameNormalizer {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public String normalizeDisplay(String rawName) {
        if (rawName == null) {
            return "";
        }
        String nfkc = Normalizer.normalize(rawName, Normalizer.Form.NFKC);
        String withoutControls = CONTROL_CHARS.matcher(nfkc).replaceAll("");
        return WHITESPACE.matcher(withoutControls.trim()).replaceAll(" ");
    }

    @Override
    public String normalizeCanonical(String rawName) {
        String display = normalizeDisplay(rawName);
        if (display.isBlank()) {
            return "";
        }
        return hasCasedLetter(display) ? display.toLowerCase(Locale.ROOT) : display;
    }

    private static boolean hasCasedLetter(String value) {
        return value.codePoints()
                .anyMatch(
                        codePoint ->
                                Character.isUpperCase(codePoint)
                                        || Character.isLowerCase(codePoint));
    }
}
