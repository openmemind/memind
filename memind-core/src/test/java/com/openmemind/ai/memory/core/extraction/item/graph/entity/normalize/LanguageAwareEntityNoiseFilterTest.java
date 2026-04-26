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

import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import org.junit.jupiter.api.Test;

class LanguageAwareEntityNoiseFilterTest {

    @Test
    void languageAwareNoiseFilterDropsPronounTemporalAndDateLikeMentions() {
        var filter = new LanguageAwareEntityNoiseFilter();

        assertThat(filter.dropReason("mine", GraphEntityType.OTHER))
                .contains(EntityDropReason.PRONOUN_LIKE);
        assertThat(filter.dropReason("今天", GraphEntityType.CONCEPT))
                .contains(EntityDropReason.TEMPORAL);
        assertThat(filter.dropReason("今天", GraphEntityType.SPECIAL))
                .contains(EntityDropReason.TEMPORAL);
        assertThat(filter.dropReason("2026-04-18", GraphEntityType.CONCEPT))
                .contains(EntityDropReason.DATE_LIKE);
        assertThat(filter.dropReason("OpenAI", GraphEntityType.ORGANIZATION)).isEmpty();
    }

    @Test
    void languageAwareNoiseFilterMustNotRegressCurrentExactPathCoverage() {
        var filter = new LanguageAwareEntityNoiseFilter();

        assertThat(filter.dropReason("mine", GraphEntityType.OTHER))
                .contains(EntityDropReason.PRONOUN_LIKE);
        assertThat(filter.dropReason("本周", GraphEntityType.CONCEPT))
                .contains(EntityDropReason.TEMPORAL);
        assertThat(filter.dropReason("...", GraphEntityType.OTHER))
                .contains(EntityDropReason.PUNCTUATION_ONLY);
        assertThat(filter.dropReason("助手", GraphEntityType.OTHER))
                .contains(EntityDropReason.RESERVED_SPECIAL_COLLISION);
        assertThat(filter.dropReason("用户", GraphEntityType.SPECIAL)).isEmpty();
        assertThat(filter.dropReason("我", GraphEntityType.SPECIAL)).isEmpty();
        assertThat(filter.dropReason("我", GraphEntityType.OTHER))
                .contains(EntityDropReason.RESERVED_SPECIAL_COLLISION);
        assertThat(filter.dropReason("用户", GraphEntityType.OTHER))
                .contains(EntityDropReason.RESERVED_SPECIAL_COLLISION);
    }
}
