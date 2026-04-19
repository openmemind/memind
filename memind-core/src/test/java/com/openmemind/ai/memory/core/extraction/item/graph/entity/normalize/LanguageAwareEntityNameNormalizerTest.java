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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageAwareEntityNameNormalizerTest {

    @Test
    void languageAwareNameNormalizerAppliesOnlyExactSafeNormalization() {
        var normalizer = new LanguageAwareEntityNameNormalizer();

        assertThat(normalizer.normalizeDisplay("  ＯpenAI\u0000\t")).isEqualTo("OpenAI");
        assertThat(normalizer.normalizeCanonical("  ＯpenAI\u0000\t")).isEqualTo("openai");
        assertThat(normalizer.normalizeCanonical("清华大学")).isEqualTo("清华大学");
        assertThat(normalizer.normalizeCanonical("e\u0301cole")).isEqualTo("\u00e9cole");
    }

    @Test
    void languageAwareNameNormalizerDocumentsCurrentTurkishNonSupportBoundary() {
        var normalizer = new LanguageAwareEntityNameNormalizer();

        assertThat(normalizer.normalizeCanonical("Istanbul")).isEqualTo("istanbul");
        assertThat(normalizer.normalizeCanonical("İstanbul")).isEqualTo("i\u0307stanbul");
        assertThat(normalizer.normalizeCanonical("Istanbul"))
                .isNotEqualTo(normalizer.normalizeCanonical("İstanbul"));
    }

    @Test
    void
            languageAwareNameNormalizerCollapsesCjkWhitespaceWithoutRemovingMeaningfulInternalSpacing() {
        var normalizer = new LanguageAwareEntityNameNormalizer();

        assertThat(normalizer.normalizeCanonical("张\u3000\u3000三")).isEqualTo("张 三");
    }
}
