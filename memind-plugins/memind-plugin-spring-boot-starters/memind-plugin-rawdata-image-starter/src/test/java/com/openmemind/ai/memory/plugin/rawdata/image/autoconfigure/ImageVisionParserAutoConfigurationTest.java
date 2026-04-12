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
package com.openmemind.ai.memory.plugin.rawdata.image.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.rawdata.image.parser.VisionImageContentParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("ImageVisionParserAutoConfiguration Test")
class ImageVisionParserAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(ImageVisionParserAutoConfiguration.class));

    @Test
    @DisplayName("register image parser when ChatModel is present")
    void registersImageParserWhenChatModelPresent() {
        contextRunner
                .withBean(ChatModel.class, TestBeans::chatModel)
                .run(
                        context -> {
                            assertThat(context.getBeansOfType(ContentParser.class))
                                    .containsKey("imageVisionContentParser");
                            assertThat(context.getBean("imageVisionContentParser"))
                                    .isInstanceOf(VisionImageContentParser.class);
                        });
    }

    @Test
    @DisplayName("back off image parser when parser is disabled")
    void backsOffImageParserWhenParserDisabled() {
        contextRunner
                .withBean(ChatModel.class, TestBeans::chatModel)
                .withPropertyValues("memind.rawdata.image.parser-enabled=false")
                .run(context -> assertThat(context.getBeansOfType(ContentParser.class)).isEmpty());
    }

    private static final class TestBeans {
        private static ChatModel chatModel() {
            return mock(ChatModel.class);
        }
    }
}
