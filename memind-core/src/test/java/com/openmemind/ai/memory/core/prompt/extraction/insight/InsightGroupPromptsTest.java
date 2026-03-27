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
package com.openmemind.ai.memory.core.prompt.extraction.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InsightGroupPromptsTest {

    @Test
    @DisplayName(
            "Rendered prompt should require natural readable labels and reject stitched titles")
    void shouldRequireNaturalReadableLabels() {
        var insightType = createInsightType();
        var items =
                List.of(
                        new MemoryItem(
                                1L,
                                "m1",
                                "去动物园会让我安心，这是我常用的自我安抚方式。",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));

        var result = InsightGroupPrompts.build(insightType, items, List.of()).render("Chinese");

        assertThat(result.systemPrompt())
                .contains("natural, standalone theme labels")
                .contains("Do NOT stitch together a broad topic and one specific example")
                .contains("Do NOT use metadata-like labels such as dates, session notes, or")
                .contains("\"自我抚慰与动物园心安\"")
                .contains("\"2026-03-27 会话记录\"");
    }

    private static MemoryInsightType createInsightType() {
        return new MemoryInsightType(
                1L,
                "experiences",
                "What is happening or has happened to the user.",
                null,
                List.of("event"),
                400,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
