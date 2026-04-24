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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.item.graph.AliasEvidenceMode;
import com.openmemind.ai.memory.core.extraction.item.graph.CrossScriptMergePolicy;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityResolutionMode;
import com.openmemind.ai.memory.core.extraction.item.graph.UserAliasDictionary;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemGraphOptionsTest {

    @Test
    void builderDefaultsMatchCanonicalDefaults() {
        assertThat(ItemGraphOptions.builder().build()).isEqualTo(ItemGraphOptions.defaults());
    }

    @Test
    void toBuilderBuildRoundTripsEveryField() {
        assertThat(sampleOptions().toBuilder().build()).isEqualTo(sampleOptions());
    }

    @Test
    void toBuilderPreservesSupportedLanguagePackOrder() {
        assertThat(sampleOptions().toBuilder().build().supportedLanguagePacks())
                .containsExactly("en", "zh", "ja");
    }

    @Test
    void legacyWithMethodsStillMatchBuilderOutput() {
        var defaults = ItemGraphOptions.defaults();
        var aliasDictionary =
                new UserAliasDictionary(true, Map.of("organization|openai", "organization:openai"));

        assertThat(defaults.withEnabled(true))
                .isEqualTo(defaults.toBuilder().enabled(true).build());
        assertThat(defaults.withSemanticSearchHeadroom(6))
                .isEqualTo(defaults.toBuilder().semanticSearchHeadroom(6).build());
        assertThat(defaults.withSemanticLinkConcurrency(2))
                .isEqualTo(defaults.toBuilder().semanticLinkConcurrency(2).build());
        assertThat(defaults.withSemanticSourceWindowSize(64))
                .isEqualTo(defaults.toBuilder().semanticSourceWindowSize(64).build());
        assertThat(defaults.withResolutionMode(EntityResolutionMode.CONSERVATIVE))
                .isEqualTo(
                        defaults.toBuilder()
                                .resolutionMode(EntityResolutionMode.CONSERVATIVE)
                                .build());
        assertThat(defaults.withUserAliasDictionary(aliasDictionary))
                .isEqualTo(defaults.toBuilder().userAliasDictionary(aliasDictionary).build());
    }

    private static ItemGraphOptions sampleOptions() {
        return new ItemGraphOptions(
                true,
                9,
                3,
                11,
                6,
                0.84d,
                5,
                2,
                64,
                EntityResolutionMode.CONSERVATIVE,
                10,
                0.90d,
                new LinkedHashSet<>(List.of("en", "zh", "ja")),
                CrossScriptMergePolicy.OFF,
                AliasEvidenceMode.METADATA,
                new UserAliasDictionary(true, Map.of("organization|openai", "organization:openai")));
    }
}
