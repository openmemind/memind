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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.openai;

import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiEmbeddingModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiEmbeddingModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiModelProviderContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingProperties;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;

public final class OpenAiEmbeddingModelFactory implements MultiAiEmbeddingModelFactory {

    @Override
    public MultiAiEmbeddingModelProviderType providerType() {
        return MultiAiEmbeddingModelProviderType.OPENAI;
    }

    @Override
    public EmbeddingModel createEmbeddingModel(
            String modelId, String providerPrefix, MultiAiModelProviderContext context) {
        OpenAiCommonProperties commonProperties =
                context.bind(providerPrefix, OpenAiCommonProperties.class);
        OpenAiEmbeddingProperties embeddingProperties =
                context.bind(providerPrefix, OpenAiEmbeddingProperties.class);
        return new OpenAiEmbeddingAutoConfiguration()
                .openAiEmbeddingModel(
                        commonProperties,
                        embeddingProperties,
                        context.provider(ObservationRegistry.class),
                        context.provider(MeterRegistry.class),
                        context.provider(EmbeddingModelObservationConvention.class),
                        context.provider(OpenAiHttpClientBuilderCustomizer.class));
    }
}
