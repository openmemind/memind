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
package com.openmemind.ai.memory.core.extraction.item.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtractedGraphHintConverterTest {

    @Test
    void convertsEntitiesAndCausalRelations() {
        var item =
                new MemoryItemExtractionResponse.ExtractedItem(
                        "content",
                        0.9f,
                        null,
                        null,
                        List.of("resolutions"),
                        Map.of(),
                        "resolution",
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedEntity(
                                        "src/payment/calc.ts", "object", 1.5f)),
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedCausalRelation(
                                        0, 1, "enabled_by", -1.0f)));

        ExtractedGraphHints hints = ExtractedGraphHintConverter.from(item);

        assertThat(hints.entities()).hasSize(1);
        assertThat(hints.entities().getFirst().name()).isEqualTo("src/payment/calc.ts");
        assertThat(hints.entities().getFirst().salience()).isEqualTo(1.0f);
        assertThat(hints.causalRelations()).hasSize(1);
        assertThat(hints.causalRelations().getFirst().relationType()).isEqualTo("enabled_by");
        assertThat(hints.causalRelations().getFirst().strength()).isEqualTo(0.0f);
    }

    @Test
    void dropsBlankEntitiesAndIncompleteCausalRelations() {
        var item =
                new MemoryItemExtractionResponse.ExtractedItem(
                        "content",
                        0.9f,
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        "tool",
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedEntity(
                                        " ", "object", 0.5f)),
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedCausalRelation(
                                        null, 1, "enabled_by", 0.5f)));

        ExtractedGraphHints hints = ExtractedGraphHintConverter.from(item);

        assertThat(hints.entities()).isEmpty();
        assertThat(hints.causalRelations()).isEmpty();
    }
}
