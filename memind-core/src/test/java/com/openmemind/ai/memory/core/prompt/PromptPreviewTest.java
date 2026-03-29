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
package com.openmemind.ai.memory.core.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PromptPreview")
class PromptPreviewTest {

    @Test
    @DisplayName("preview should use override when present")
    void previewUsesOverrideWhenPresent() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.INTENT_ROUTING, "Custom intent routing instruction")
                        .build();

        String preview = PromptPreview.preview(registry, PromptType.INTENT_ROUTING, "Chinese");

        assertThat(preview).contains("Output Language = Chinese");
        assertThat(preview).contains("Custom intent routing instruction");
        assertThat(preview).doesNotContain("When to RETRIEVE");
    }

    @Test
    @DisplayName("preview should use the default template skeleton")
    void previewUsesDefaultTemplateSkeletonForTypedQueryExpand() {
        String preview =
                PromptPreview.preview(
                        PromptRegistry.EMPTY, PromptType.TYPED_QUERY_EXPAND, "English");

        assertThat(preview).contains("fill the identified gaps");
        assertThat(preview).contains("# Time-Aware Expansion");
        assertThat(preview).contains("{{max_expansions}}");
    }

    @Test
    @DisplayName("previewExpanded should resolve internal placeholders for memory item unified")
    void previewExpandedResolvesInternalPlaceholdersForMemoryItemUnified() {
        String preview =
                PromptPreview.previewExpanded(
                        PromptRegistry.EMPTY, PromptType.MEMORY_ITEM_UNIFIED, "English");

        assertThat(preview).contains("## Category Definitions");
        assertThat(preview).contains("# Identity");
        assertThat(preview).contains("# Subject Clarity");
        assertThat(preview).contains("# Temporal Resolution");
        assertThat(preview).doesNotContain("{{CATEGORY_CONTEXT}}");
        assertThat(preview).doesNotContain("{{IDENTITY_CONTEXT}}");
        assertThat(preview).doesNotContain("{{SUBJECT_CONTEXT}}");
        assertThat(preview).doesNotContain("{{TEMPORAL_CONTEXT}}");
    }

    @Test
    @DisplayName("previewExpanded should resolve configurable placeholders for typed query expand")
    void previewExpandedResolvesConfigurablePlaceholdersForTypedQueryExpand() {
        String preview =
                PromptPreview.previewExpanded(
                        PromptRegistry.EMPTY, PromptType.TYPED_QUERY_EXPAND, "English");

        assertThat(preview).contains("Maximum 5 queries");
        assertThat(preview).doesNotContain("{{max_expansions}}");
    }

    @Test
    @DisplayName("previewExpanded should resolve sample insight type placeholders")
    void previewExpandedResolvesSampleInsightTypePlaceholders() {
        String preview =
                PromptPreview.previewExpanded(
                        PromptRegistry.EMPTY, PromptType.INSIGHT_GROUP, "English");

        assertThat(preview)
                .contains("You are grouping items for the insight dimension: \"identity\"");
        assertThat(preview).doesNotContain("{{insight_type_name}}");
        assertThat(preview).doesNotContain("{{insight_type_description}}");
    }

    @Test
    @DisplayName("previewExpanded should use override when present")
    void previewExpandedUsesOverrideWhenPresent() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.MEMORY_ITEM_UNIFIED, "Custom memory item instruction")
                        .build();

        String preview =
                PromptPreview.previewExpanded(registry, PromptType.MEMORY_ITEM_UNIFIED, "English");

        assertThat(preview).contains("Custom memory item instruction");
        assertThat(preview).doesNotContain("## Category Definitions");
    }

    @Test
    @DisplayName("preview should support every prompt type default template")
    void previewSupportsEveryPromptTypeDefaultTemplate() {
        for (PromptType type : PromptType.values()) {
            String preview = PromptPreview.preview(PromptRegistry.EMPTY, type, "English");

            assertThat(preview)
                    .as("preview for %s", type)
                    .contains("Output Language = English")
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("previewExpanded should support every prompt type default template")
    void previewExpandedSupportsEveryPromptTypeDefaultTemplate() {
        for (PromptType type : PromptType.values()) {
            String preview = PromptPreview.previewExpanded(PromptRegistry.EMPTY, type, "English");

            assertThat(preview)
                    .as("expanded preview for %s", type)
                    .contains("Output Language = English")
                    .isNotBlank();
        }
    }
}
