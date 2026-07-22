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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

@DisplayName("Multi chat model")
class MultiChatModelTest {

    @Test
    @DisplayName("exposes configured chat models by model id")
    void exposesConfiguredChatModelsByModelId() {
        RecordingChatModel defaultModel = new RecordingChatModel("default");
        RecordingChatModel reasoningModel = new RecordingChatModel("reasoning");
        Map<String, ChatModel> chatModels = new LinkedHashMap<>();
        chatModels.put("default", defaultModel);
        chatModels.put("reasoning", reasoningModel);

        MultiChatModel multiChatModel = new MultiChatModel("default", chatModels);

        assertThat(multiChatModel.getDefaultChatModelId()).isEqualTo("default");
        assertThat(multiChatModel.getDefaultChatModel()).isSameAs(defaultModel);
        assertThat(multiChatModel.getChatModel("reasoning")).isSameAs(reasoningModel);
        assertThat(multiChatModel.getChatModels())
                .containsExactlyEntriesOf(chatModels)
                .isNotSameAs(chatModels);
        assertThatThrownBy(() -> multiChatModel.getChatModels().put("vision", reasoningModel))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("delegates ChatModel calls to the default chat model")
    void delegatesChatModelCallsToDefaultChatModel() {
        RecordingChatModel defaultModel = new RecordingChatModel("default");
        RecordingChatModel reasoningModel = new RecordingChatModel("reasoning");
        MultiChatModel multiChatModel =
                new MultiChatModel(
                        "default", Map.of("default", defaultModel, "reasoning", reasoningModel));
        Prompt prompt = new Prompt("hello");

        ChatResponse response = multiChatModel.call(prompt);
        List<ChatResponse> streamResponses = multiChatModel.stream(prompt).collectList().block();

        assertThat(response).isSameAs(defaultModel.callResponse);
        assertThat(streamResponses).containsExactly(defaultModel.streamResponse);
        assertThat(multiChatModel.getOptions()).isSameAs(defaultModel.options);
        assertThat(defaultModel.callPrompts).containsExactly(prompt);
        assertThat(defaultModel.streamPrompts).containsExactly(prompt);
        assertThat(reasoningModel.callPrompts).isEmpty();
        assertThat(reasoningModel.streamPrompts).isEmpty();
    }

    @Test
    @DisplayName("fails clearly when a requested chat model id is missing")
    void failsClearlyWhenRequestedChatModelIdIsMissing() {
        MultiChatModel multiChatModel =
                new MultiChatModel("default", Map.of("default", new RecordingChatModel("default")));

        assertThatThrownBy(() -> multiChatModel.getChatModel("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("fails clearly when the default chat model id is missing")
    void failsClearlyWhenDefaultChatModelIdIsMissing() {
        assertThatThrownBy(
                        () ->
                                new MultiChatModel(
                                        "default",
                                        Map.of("reasoning", new RecordingChatModel("reasoning"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default");
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private static final class RecordingChatModel implements ChatModel {

        private final ChatResponse callResponse;

        private final ChatResponse streamResponse;

        private final ChatOptions options;

        private final List<Prompt> callPrompts = new java.util.ArrayList<>();

        private final List<Prompt> streamPrompts = new java.util.ArrayList<>();

        private RecordingChatModel(String modelId) {
            this.callResponse = response(modelId + "-call");
            this.streamResponse = response(modelId + "-stream");
            this.options = ChatOptions.builder().model(modelId).build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            callPrompts.add(prompt);
            return callResponse;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            streamPrompts.add(prompt);
            return Flux.just(streamResponse);
        }

        @Override
        public ChatOptions getOptions() {
            return options;
        }
    }
}
