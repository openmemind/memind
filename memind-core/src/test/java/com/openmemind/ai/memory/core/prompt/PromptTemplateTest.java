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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PromptTemplateTest {

    @Nested
    @DisplayName("Builder and render")
    class BuilderAndRender {

        @Test
        void basicSectionsAndVariables() {
            var template =
                    PromptTemplate.builder("test")
                            .section("role", "You are a {{role_name}} expert.")
                            .section("task", "Analyze the {{target}}.")
                            .userPrompt("Input: {{input_data}}")
                            .variable("role_name", "memory")
                            .variable("target", "patterns")
                            .variable("input_data", "hello world")
                            .build();

            var result = template.render("English");

            assertThat(result.systemPrompt()).contains("You are a memory expert.");
            assertThat(result.systemPrompt()).contains("Analyze the patterns.");
            assertThat(result.userPrompt()).isEqualTo("Input: hello world");
            assertThat(result.hasSystemPrompt()).isTrue();
        }

        @Test
        void sectionOrderPreserved() {
            var template =
                    PromptTemplate.builder("test")
                            .section("first", "AAA")
                            .section("second", "BBB")
                            .section("third", "CCC")
                            .userPrompt("user")
                            .build();

            var result = template.render("English");
            var system = result.systemPrompt();
            assertThat(system.indexOf("AAA")).isLessThan(system.indexOf("BBB"));
            assertThat(system.indexOf("BBB")).isLessThan(system.indexOf("CCC"));
        }

        @Test
        void emptySectionsSkipped() {
            var template =
                    PromptTemplate.builder("test")
                            .section("filled", "content")
                            .section("empty", "")
                            .section("blank", "   ")
                            .userPrompt("user")
                            .build();

            var result = template.render("English");
            assertThat(result.systemPrompt()).contains("content");
            assertThat(result.systemPrompt()).doesNotContain("   ");
        }

        @Test
        void userOnlyWhenNoSections() {
            var template =
                    PromptTemplate.builder("test")
                            .userPrompt("just user prompt {{name}}")
                            .variable("name", "test")
                            .build();

            var result = template.render("English");
            assertThat(result.hasSystemPrompt()).isFalse();
            assertThat(result.userPrompt()).isEqualTo("just user prompt test");
        }

        @Test
        void languageRuleInjected() {
            var template =
                    PromptTemplate.builder("test")
                            .section("role", "You are an expert.")
                            .userPrompt("input")
                            .build();

            var result = template.render("Chinese");
            assertThat(result.systemPrompt()).contains("Chinese");
        }
    }

    @Nested
    @DisplayName("Variable validation")
    class VariableValidation {

        @Test
        void unresolvedVariableThrows() {
            var template =
                    PromptTemplate.builder("test")
                            .section("role", "You are a {{role_name}} expert.")
                            .userPrompt("input")
                            .build();

            assertThatThrownBy(() -> template.render("English"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("role_name")
                    .hasMessageContaining("test");
        }

        @Test
        void jsonBracesNotTreatedAsVariables() {
            var template =
                    PromptTemplate.builder("test")
                            .section(
                                    "output",
                                    """
                                    Return JSON:
                                    {
                                      "key": "value",
                                      "nested": { "a": 1 }
                                    }
                                    """)
                            .userPrompt("input")
                            .build();

            // Should not throw — single braces are not variables
            var result = template.render("English");
            assertThat(result.systemPrompt()).contains("\"key\": \"value\"");
        }
    }

    @Nested
    @DisplayName("Immutable with* methods")
    class ImmutableTransformations {

        @Test
        void withSectionAddsOrReplaces() {
            var original =
                    PromptTemplate.builder("test")
                            .section("role", "original")
                            .userPrompt("user")
                            .build();

            var modified = original.withSection("role", "replaced");
            var added = original.withSection("task", "new section");

            // original unchanged
            assertThat(original.render("English").systemPrompt()).contains("original");
            // modified has replacement
            assertThat(modified.render("English").systemPrompt()).contains("replaced");
            assertThat(modified.render("English").systemPrompt()).doesNotContain("original");
            // added has both
            assertThat(added.render("English").systemPrompt()).contains("original");
            assertThat(added.render("English").systemPrompt()).contains("new section");
        }

        @Test
        void withoutSectionRemoves() {
            var original =
                    PromptTemplate.builder("test")
                            .section("role", "keep")
                            .section("rules", "remove me")
                            .userPrompt("user")
                            .build();

            var modified = original.withoutSection("rules");

            assertThat(modified.render("English").systemPrompt()).doesNotContain("remove me");
            assertThat(modified.render("English").systemPrompt()).contains("keep");
            // original unchanged
            assertThat(original.render("English").systemPrompt()).contains("remove me");
        }

        @Test
        void withVariableAdds() {
            var original =
                    PromptTemplate.builder("test")
                            .section("role", "Expert in {{domain}}")
                            .userPrompt("user")
                            .variable("domain", "AI")
                            .build();

            var modified = original.withVariable("domain", "ML");

            assertThat(original.render("English").systemPrompt()).contains("Expert in AI");
            assertThat(modified.render("English").systemPrompt()).contains("Expert in ML");
        }
    }

    @Nested
    @DisplayName("jsonSafeVariable")
    class JsonSafe {

        @Test
        void escapesSpecialCharacters() {
            var template =
                    PromptTemplate.builder("test")
                            .section("output", "Content: {{data}}")
                            .userPrompt("user")
                            .jsonSafeVariable("data", "line1\nline2\ttab\"quote\\back")
                            .build();

            var result = template.render("English");
            assertThat(result.systemPrompt()).contains("line1\\nline2\\ttab\\\"quote\\\\back");
        }
    }

    @Nested
    @DisplayName("Debug support")
    class DebugSupport {

        @Test
        void describeStructure() {
            var template =
                    PromptTemplate.builder("insight-analyze")
                            .section("objective", "obj content")
                            .section("workflow", "wf content")
                            .userPrompt("user {{x}}")
                            .variable("x", "val")
                            .build();

            var desc = template.describeStructure();
            assertThat(desc).contains("insight-analyze");
            assertThat(desc).contains("objective");
            assertThat(desc).contains("workflow");
            assertThat(desc).contains("x=val");
        }

        @Test
        void preview() {
            var template =
                    PromptTemplate.builder("test")
                            .section("role", "You are an expert.")
                            .userPrompt("Hello")
                            .build();

            var preview = template.preview("English");
            assertThat(preview).contains("=== SYSTEM ===");
            assertThat(preview).contains("=== USER ===");
            assertThat(preview).contains("You are an expert.");
            assertThat(preview).contains("Hello");
        }
    }
}
