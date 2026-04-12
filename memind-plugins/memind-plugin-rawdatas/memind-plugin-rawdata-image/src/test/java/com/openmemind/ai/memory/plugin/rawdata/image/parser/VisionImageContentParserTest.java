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
package com.openmemind.ai.memory.plugin.rawdata.image.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.resource.SourceKind;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class VisionImageContentParserTest {

    @Test
    void supportsOctetStreamByImageExtension() {
        var parser =
                new VisionImageContentParser(
                        chatModel(
                                """
                                {"description":"desc","ocrText":"ocr","metadata":{"provider":"spring-ai"}}
                                """));

        assertThat(
                        parser.supports(
                                new SourceDescriptor(
                                        SourceKind.FILE,
                                        "chart.png",
                                        "application/octet-stream",
                                        3L,
                                        null)))
                .isTrue();
    }

    @Test
    void parserMapsAnalyzerResultIntoImageContent() {
        var parser =
                new VisionImageContentParser(
                        chatModel(
                                """
                                {
                                  "description":"dashboard screenshot",
                                  "ocrText":"total revenue 30%",
                                  "metadata":{"provider":"spring-ai"}
                                }
                                """));

        var content =
                (ImageContent)
                        parser.parse(
                                        new byte[] {1, 2, 3},
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "chart.png",
                                                "image/png",
                                                3L,
                                                null))
                                .block();

        assertThat(content).isNotNull();
        assertThat(content.description()).isEqualTo("dashboard screenshot");
        assertThat(content.ocrText()).isEqualTo("total revenue 30%");
        assertThat(content.metadata())
                .containsEntry("parserId", "image-vision")
                .containsEntry("contentProfile", "image.caption-ocr")
                .containsEntry("provider", "spring-ai");
    }

    @SuppressWarnings("unchecked")
    private static ChatModel chatModel(String responseText) {
        ChatResponse response =
                new ChatResponse(
                        java.util.List.of(new Generation(new AssistantMessage(responseText))));
        return (ChatModel)
                Proxy.newProxyInstance(
                        VisionImageContentParserTest.class.getClassLoader(),
                        new Class<?>[] {ChatModel.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "call" -> {
                                        if (args.length == 1 && args[0] instanceof Prompt) {
                                            yield response;
                                        }
                                        throw new UnsupportedOperationException(method.getName());
                                    }
                                    case "toString" -> "FakeChatModel";
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
