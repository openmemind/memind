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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiEmbeddingModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiEmbeddingModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.anthropic.AnthropicChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.google.GoogleGenAiChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.google.GoogleGenAiEmbeddingModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.openai.OpenAiChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.openai.OpenAiEmbeddingModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.chat.MultiChatModel;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.embedding.MultiEmbeddingModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Multi Spring AI model auto-configuration")
class MultiAiModelAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(MultiAiModelAutoConfiguration.class));

    private final ApplicationContextRunner autoConfigurationRunner =
            new ApplicationContextRunner().withUserConfiguration(MultiAiModelApplication.class);

    @Test
    @DisplayName("registers configured chat models inside a multi chat model")
    void registersConfiguredChatModelsInsideMultiChatModel() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.chat-models.default.type=openai",
                        "spring.ai.chat-models.default.openai.api-key=test-key",
                        "spring.ai.chat-models.default.openai.model=gpt-4o-mini",
                        "spring.ai.chat-models.reasoning.type=anthropic",
                        "spring.ai.chat-models.reasoning.anthropic.api-key=test-key",
                        "spring.ai.chat-models.reasoning.anthropic.model=claude-3-5-sonnet-latest",
                        "spring.ai.chat-models.vision.type=google",
                        "spring.ai.chat-models.vision.google.api-key=test-key",
                        "spring.ai.chat-models.vision.google.model=gemini-2.5-flash")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(MultiChatModel.class);
                            assertThat(context.getBean(ChatModel.class))
                                    .isSameAs(context.getBean(MultiChatModel.class));
                            assertThat(
                                            context.getBeanFactory()
                                                    .getBeanDefinition("multiChatModel")
                                                    .getFactoryMethodName())
                                    .isEqualTo("multiChatModel");
                            assertThat(context.getBeanNamesForType(ChatModel.class))
                                    .containsExactly("multiChatModel");
                            assertThat(context).doesNotHaveBean("default");
                            assertThat(context).doesNotHaveBean("reasoning");
                            assertThat(context).doesNotHaveBean("vision");
                            MultiChatModel multiChatModel = context.getBean(MultiChatModel.class);
                            assertThat(multiChatModel.getDefaultChatModelId()).isEqualTo("default");
                            assertThat(multiChatModel.getDefaultChatModel())
                                    .isInstanceOf(OpenAiChatModel.class);
                            assertThat(
                                            context.getBeanFactory()
                                                    .getBeanDefinition("multiChatModel")
                                                    .isPrimary())
                                    .isTrue();
                            assertThat(multiChatModel.getChatModel("default"))
                                    .isInstanceOf(OpenAiChatModel.class);
                            assertThat(multiChatModel.getChatModel("reasoning"))
                                    .isInstanceOf(AnthropicChatModel.class);
                            assertThat(multiChatModel.getChatModel("vision"))
                                    .isInstanceOf(GoogleGenAiChatModel.class);
                        });
    }

    @Test
    @DisplayName("registers configured embedding models inside a multi embedding model")
    void registersConfiguredEmbeddingModelsInsideMultiEmbeddingModel() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.embedding-models.default.type=openai",
                        "spring.ai.embedding-models.default.openai.api-key=test-key",
                        "spring.ai.embedding-models.default.openai.model=text-embedding-3-small",
                        "spring.ai.embedding-models.default.openai.dimensions=1536",
                        "spring.ai.embedding-models.googleEmbedding.type=google",
                        "spring.ai.embedding-models.googleEmbedding.google.api-key=test-key",
                        "spring.ai.embedding-models.googleEmbedding.google.model=text-embedding-004",
                        "spring.ai.embedding-models.googleEmbedding.google.dimensions=768")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(MultiEmbeddingModel.class);
                            assertThat(context.getBean(EmbeddingModel.class))
                                    .isSameAs(context.getBean(MultiEmbeddingModel.class));
                            assertThat(
                                            context.getBeanFactory()
                                                    .getBeanDefinition("multiEmbeddingModel")
                                                    .getFactoryMethodName())
                                    .isEqualTo("multiEmbeddingModel");
                            assertThat(context.getBeanNamesForType(EmbeddingModel.class))
                                    .containsExactly("multiEmbeddingModel");
                            assertThat(context).doesNotHaveBean("default");
                            assertThat(context).doesNotHaveBean("googleEmbedding");
                            MultiEmbeddingModel multiEmbeddingModel =
                                    context.getBean(MultiEmbeddingModel.class);
                            assertThat(multiEmbeddingModel.getDefaultEmbeddingModelId())
                                    .isEqualTo("default");
                            assertThat(multiEmbeddingModel.getDefaultEmbeddingModel())
                                    .isInstanceOf(OpenAiEmbeddingModel.class);
                            assertThat(
                                            context.getBeanFactory()
                                                    .getBeanDefinition("multiEmbeddingModel")
                                                    .isPrimary())
                                    .isTrue();
                            assertThat(multiEmbeddingModel.getEmbeddingModel("default"))
                                    .isInstanceOf(OpenAiEmbeddingModel.class);
                            assertThat(multiEmbeddingModel.getEmbeddingModel("googleEmbedding"))
                                    .isInstanceOf(GoogleGenAiTextEmbeddingModel.class);
                        });
    }

    @Test
    @DisplayName("uses spring.ai.model.chat as the primary chat model id")
    void usesConfiguredPrimaryChatModelId() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.model.chat=reasoning",
                        "spring.ai.chat-models.default.type=openai",
                        "spring.ai.chat-models.default.openai.api-key=test-key",
                        "spring.ai.chat-models.default.openai.model=gpt-4o-mini",
                        "spring.ai.chat-models.reasoning.type=anthropic",
                        "spring.ai.chat-models.reasoning.anthropic.api-key=test-key",
                        "spring.ai.chat-models.reasoning.anthropic.model=claude-3-5-sonnet-latest")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(ChatModel.class))
                                    .isSameAs(context.getBean(MultiChatModel.class));
                            MultiChatModel multiChatModel = context.getBean(MultiChatModel.class);
                            assertThat(multiChatModel.getDefaultChatModelId())
                                    .isEqualTo("reasoning");
                            assertThat(multiChatModel.getDefaultChatModel())
                                    .isInstanceOf(AnthropicChatModel.class);
                            assertThat(
                                            context.getBeanFactory()
                                                    .getBeanDefinition("multiChatModel")
                                                    .isPrimary())
                                    .isTrue();
                            assertThat(context).doesNotHaveBean("default");
                            assertThat(context).doesNotHaveBean("reasoning");
                        });
    }

    @Test
    @DisplayName("uses spring.ai.model.embedding as the primary embedding model id")
    void usesConfiguredPrimaryEmbeddingModelId() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.model.embedding=googleEmbedding",
                        "spring.ai.embedding-models.default.type=openai",
                        "spring.ai.embedding-models.default.openai.api-key=test-key",
                        "spring.ai.embedding-models.default.openai.model=text-embedding-3-small",
                        "spring.ai.embedding-models.googleEmbedding.type=google",
                        "spring.ai.embedding-models.googleEmbedding.google.api-key=test-key",
                        "spring.ai.embedding-models.googleEmbedding.google.model=text-embedding-004")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(EmbeddingModel.class))
                                    .isSameAs(context.getBean(MultiEmbeddingModel.class));
                            MultiEmbeddingModel multiEmbeddingModel =
                                    context.getBean(MultiEmbeddingModel.class);
                            assertThat(multiEmbeddingModel.getDefaultEmbeddingModelId())
                                    .isEqualTo("googleEmbedding");
                            assertThat(multiEmbeddingModel.getDefaultEmbeddingModel())
                                    .isInstanceOf(GoogleGenAiTextEmbeddingModel.class);
                            assertThat(
                                            context.getBeanFactory()
                                                    .getBeanDefinition("multiEmbeddingModel")
                                                    .isPrimary())
                                    .isTrue();
                            assertThat(context).doesNotHaveBean("default");
                            assertThat(context).doesNotHaveBean("googleEmbedding");
                        });
    }

    @Test
    @DisplayName("registers provider model factories as strategy beans")
    void registersProviderModelFactoriesAsStrategyBeans() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(MultiAiChatModelFactory.class))
                            .containsOnlyKeys(
                                    "openAiChatModelFactory",
                                    "anthropicChatModelFactory",
                                    "googleGenAiChatModelFactory");
                    assertThat(context.getBeansOfType(MultiAiChatModelFactory.class).values())
                            .extracting(MultiAiChatModelFactory::providerType)
                            .containsExactlyInAnyOrder(
                                    MultiAiChatModelProviderType.OPENAI,
                                    MultiAiChatModelProviderType.ANTHROPIC,
                                    MultiAiChatModelProviderType.GOOGLE);
                    assertThat(context.getBeansOfType(MultiAiEmbeddingModelFactory.class))
                            .containsOnlyKeys(
                                    "openAiEmbeddingModelFactory",
                                    "googleGenAiEmbeddingModelFactory");
                    assertThat(context.getBeansOfType(MultiAiEmbeddingModelFactory.class).values())
                            .extracting(MultiAiEmbeddingModelFactory::providerType)
                            .containsExactlyInAnyOrder(
                                    MultiAiEmbeddingModelProviderType.OPENAI,
                                    MultiAiEmbeddingModelProviderType.GOOGLE);
                    assertThat(context).hasSingleBean(OpenAiChatModelFactory.class);
                    assertThat(context).hasSingleBean(AnthropicChatModelFactory.class);
                    assertThat(context).hasSingleBean(GoogleGenAiChatModelFactory.class);
                    assertThat(context).hasSingleBean(OpenAiEmbeddingModelFactory.class);
                    assertThat(context).hasSingleBean(GoogleGenAiEmbeddingModelFactory.class);
                });
    }

    @Test
    @DisplayName("fails clearly when a configured model type is unsupported")
    void failsClearlyWhenConfiguredModelTypeIsUnsupported() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.chat-models.default.type=ollama",
                        "spring.ai.chat-models.default.openai.model=gpt-4o-mini")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasStackTraceContaining(
                                                "spring.ai.chat-models.default.type")
                                        .hasStackTraceContaining("ollama"));
    }

    @Test
    @DisplayName("fails clearly when an unsupported embedding provider is configured")
    void failsClearlyWhenUnsupportedEmbeddingProviderIsConfigured() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.embedding-models.claude.type=anthropic",
                        "spring.ai.embedding-models.claude.anthropic.api-key=test-key",
                        "spring.ai.embedding-models.claude.anthropic.model=claude-3-5-sonnet-latest")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasStackTraceContaining(
                                                "spring.ai.embedding-models.claude.type")
                                        .hasStackTraceContaining("anthropic"));
    }

    @Test
    @DisplayName("backs off Spring AI single model provider auto-configurations")
    void backsOffSpringAiSingleModelProviderAutoConfigurations() {
        autoConfigurationRunner
                .withPropertyValues(
                        "spring.ai.chat-models.default.type=openai",
                        "spring.ai.chat-models.default.openai.api-key=test-key",
                        "spring.ai.chat-models.default.openai.model=gpt-4o-mini")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(ChatModel.class))
                                    .isInstanceOf(MultiChatModel.class);
                            assertThat(context.getBean(MultiChatModel.class).getDefaultChatModel())
                                    .isInstanceOf(OpenAiChatModel.class);
                            assertThat(context).doesNotHaveBean("default");
                            assertThat(context).doesNotHaveBean("openAiChatModel");
                        });
    }

    @Test
    @DisplayName(
            "does not trigger Spring AI single model provider auto-configurations without multi"
                    + " model configuration")
    void
            doesNotTriggerSpringAiSingleModelProviderAutoConfigurationsWithoutMultiModelConfiguration() {
        autoConfigurationRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChatModel.class);
                    assertThat(context).doesNotHaveBean(EmbeddingModel.class);
                });
    }

    @Test
    @DisplayName("does not require unused provider libraries")
    void doesNotRequireUnusedProviderLibraries() {
        new ApplicationContextRunner()
                .withClassLoader(
                        new FilteredClassLoader(
                                "com.anthropic",
                                "com.google.genai",
                                "org.springframework.ai.anthropic",
                                "org.springframework.ai.google",
                                "org.springframework.ai.model.anthropic",
                                "org.springframework.ai.model.google"))
                .withConfiguration(AutoConfigurations.of(MultiAiModelAutoConfiguration.class))
                .withPropertyValues(
                        "spring.ai.chat-models.default.type=openai",
                        "spring.ai.chat-models.default.openai.api-key=test-key",
                        "spring.ai.chat-models.default.openai.model=gpt-4o-mini")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(MultiChatModel.class).getDefaultChatModel())
                                    .isInstanceOf(OpenAiChatModel.class);
                            assertThat(context).doesNotHaveBean("default");
                        });
    }

    @Test
    @DisplayName("does not require embedding provider libraries for chat models")
    void doesNotRequireEmbeddingProviderLibrariesForChatModels() {
        new ApplicationContextRunner()
                .withClassLoader(
                        new FilteredClassLoader(
                                "org.springframework.ai.model.openai.autoconfigure"
                                        + ".OpenAiEmbeddingAutoConfiguration",
                                "org.springframework.ai.openai.OpenAiEmbeddingModel"))
                .withConfiguration(AutoConfigurations.of(MultiAiModelAutoConfiguration.class))
                .withPropertyValues(
                        "spring.ai.chat-models.default.type=openai",
                        "spring.ai.chat-models.default.openai.api-key=test-key",
                        "spring.ai.chat-models.default.openai.model=gpt-4o-mini")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(MultiChatModel.class).getDefaultChatModel())
                                    .isInstanceOf(OpenAiChatModel.class);
                            assertThat(context).doesNotHaveBean("default");
                        });
    }

    @Test
    @DisplayName("fails clearly when a configured provider factory is missing")
    void failsClearlyWhenConfiguredProviderFactoryIsMissing() {
        new ApplicationContextRunner()
                .withClassLoader(
                        new FilteredClassLoader(
                                "com.openai",
                                "org.springframework.ai.model.openai",
                                "org.springframework.ai.openai"))
                .withConfiguration(AutoConfigurations.of(MultiAiModelAutoConfiguration.class))
                .withPropertyValues(
                        "spring.ai.chat-models.default.type=openai",
                        "spring.ai.chat-models.default.openai.api-key=test-key",
                        "spring.ai.chat-models.default.openai.model=gpt-4o-mini")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasStackTraceContaining(
                                                "spring.ai.chat-models.default.type=openai requires"
                                                        + " Spring AI OpenAI chat dependencies"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class MultiAiModelApplication {}
}
