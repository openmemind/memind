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
package com.openmemind.ai.memory.core.item.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.prompt.extraction.item.MemoryItemPrompts;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MemoryItemPrompts Unit Test")
class MemoryItemExtractionPromptBuilderTest {

    @Nested
    @DisplayName("Unified Mode")
    class UnifiedTests {

        @Test
        @DisplayName(
                "system prompt should not contain Category Guide (Unified mode has no categories)")
        void systemPromptShouldNotContainCategoryGuide() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy")
                            .render("English")
                            .systemPrompt();
            assertThat(prompt).doesNotContain("Category Guide");
        }

        @Test
        @DisplayName("user prompt should contain segment text")
        void userPromptShouldContainSegmentText() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "用户: 我喜欢喝咖啡")
                            .render("English")
                            .userPrompt();
            assertThat(prompt).contains("用户: 我喜欢喝咖啡");
        }

        @Test
        @DisplayName("system prompt should contain insightTypes output constraints")
        void systemPromptShouldContainInsightTypeRules() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy")
                            .render("English")
                            .systemPrompt();
            assertThat(prompt).contains("Available insightTypes").contains("insightTypes");
        }

        @Test
        @DisplayName("system prompt should contain Scoring Guidelines")
        void systemPromptShouldContainScoringGuidelines() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy")
                            .render("English")
                            .systemPrompt();
            assertThat(prompt).contains("# Scoring Guidelines");
            assertThat(prompt).contains("## confidence");
            assertThat(prompt).contains("## occurredAt");
        }

        @Test
        @DisplayName("should contain Reference Date when referenceTime is provided")
        void systemPromptShouldContainReferenceDateWhenProvided() {
            var insightTypes = DefaultInsightTypes.all();
            var refTime = Instant.parse("2025-02-07T10:00:00Z");
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy", refTime)
                            .render("English")
                            .systemPrompt();
            assertThat(prompt).contains("# Temporal Resolution");
            assertThat(prompt).contains("Today's date: 2025-02-07 (Friday)");
        }

        @Test
        @DisplayName("system prompt should contain Core Principles")
        void systemPromptShouldContainCorePrinciples() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy")
                            .render("English")
                            .systemPrompt();
            assertThat(prompt).contains("Core Principles");
        }

        @Test
        @DisplayName("system prompt should contain Atomicity principle")
        void systemPromptShouldContainAtomicityPrinciple() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy")
                            .render("English")
                            .systemPrompt();
            assertThat(prompt).contains("Atomicity");
        }

        @Test
        @DisplayName("system prompt should contain Extraction Bias")
        void systemPromptShouldContainExtractionBias() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy")
                            .render("English")
                            .systemPrompt();
            assertThat(prompt).contains("Extraction Bias");
        }

        @Test
        @DisplayName("system prompt should contain What NOT to Extract rules")
        void systemPromptShouldContainDoNotExtractRules() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy")
                            .render("English")
                            .systemPrompt();
            assertThat(prompt).contains("What NOT to Extract");
        }

        @Test
        @DisplayName("system prompt should contain Examples")
        void systemPromptShouldContainExamples() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy")
                            .render("English")
                            .systemPrompt();
            assertThat(prompt).contains("# Examples");
        }
    }

    @Nested
    @DisplayName("Scoring Guidelines Content")
    class ScoringGuidelinesTests {

        @Test
        @DisplayName("confidence should contain 4 tiers")
        void shouldContainConfidenceTiers() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy")
                            .render("English")
                            .systemPrompt();
            assertThat(prompt)
                    .contains("0.95-1.0")
                    .contains("0.85-0.94")
                    .contains("0.70-0.84")
                    .contains("< 0.70");
        }

        @Test
        @DisplayName("occurredAt should contain time parsing rules")
        void shouldContainEventTimeRules() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy")
                            .render("English")
                            .systemPrompt();
            assertThat(prompt)
                    .contains("Temporal Content Embedding")
                    .contains("Time-specific memories")
                    .contains("Non-temporal items")
                    .contains("ISO-8601");
        }
    }

    @Nested
    @DisplayName("Workflow Content Validation")
    class WorkflowTests {

        @Test
        @DisplayName("buildUnified system prompt should contain Category Classification block")
        void unifiedPromptShouldContainCategoriesBlock() {
            var insightTypes = DefaultInsightTypes.all();
            String prompt =
                    MemoryItemPrompts.buildUnified(insightTypes, "dummy")
                            .render("English")
                            .systemPrompt();
            assertThat(prompt).contains("# Category Classification");
        }
    }
}
