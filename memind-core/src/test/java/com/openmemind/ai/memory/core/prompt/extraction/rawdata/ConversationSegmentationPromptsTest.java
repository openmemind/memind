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
package com.openmemind.ai.memory.core.prompt.extraction.rawdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConversationSegmentationPrompts")
class ConversationSegmentationPromptsTest {

    @Test
    @DisplayName("Build named system sections and keep indexed conversation in user payload")
    void builds_named_sections_and_keeps_indexed_conversation_in_user_payload() {
        var messages =
                List.of(
                        Message.user("Hello"),
                        Message.assistant("Hi there"),
                        Message.user("Let's discuss the Redis cache rollout"));

        var template = ConversationSegmentationPrompts.build(messages, 2);
        var prompt = template.render("English");

        assertThat(template.describeStructure())
                .contains("Sections: objective, guidelines, output, examples");
        assertThat(prompt.userPrompt())
                .contains("# Conversation")
                .contains("[0] user: Hello")
                .contains("[2] user: Let's discuss the Redis cache rollout")
                .doesNotContain("# Where to Split");
    }

    @Test
    @DisplayName("build with registry should replace segmentation instructions")
    void buildWithRegistryUsesOverrideInstruction() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(
                                PromptType.CONVERSATION_SEGMENTATION,
                                "Custom segmentation instruction")
                        .build();
        var template =
                ConversationSegmentationPrompts.build(
                        registry,
                        List.of(
                                Message.user("Hello"),
                                Message.assistant("Hi there"),
                                Message.user("Let's discuss the Redis cache rollout")),
                        2);
        var prompt = template.render("English");

        assertThat(prompt.systemPrompt()).contains("Custom segmentation instruction");
        assertThat(prompt.systemPrompt()).doesNotContain("# Workflow");
    }
}
