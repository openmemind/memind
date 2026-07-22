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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.google;

import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.MultiAiModelConfigurationException;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiEmbeddingModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiEmbeddingModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiModelProviderContext;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionProperties;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingProperties;
import org.springframework.core.retry.RetryTemplate;

public final class GoogleGenAiEmbeddingModelFactory implements MultiAiEmbeddingModelFactory {

    @Override
    public MultiAiEmbeddingModelProviderType providerType() {
        return MultiAiEmbeddingModelProviderType.GOOGLE;
    }

    @Override
    public EmbeddingModel createEmbeddingModel(
            String modelId, String providerPrefix, MultiAiModelProviderContext context) {
        GoogleGenAiEmbeddingConnectionProperties connectionProperties =
                context.bind(providerPrefix, GoogleGenAiEmbeddingConnectionProperties.class);
        GoogleGenAiTextEmbeddingProperties embeddingProperties =
                context.bind(providerPrefix, GoogleGenAiTextEmbeddingProperties.class);
        GoogleGenAiEmbeddingConnectionDetails connectionDetails;
        try {
            connectionDetails =
                    new GoogleGenAiEmbeddingConnectionAutoConfiguration()
                            .googleGenAiEmbeddingConnectionDetails(connectionProperties);
        } catch (IOException exception) {
            throw new MultiAiModelConfigurationException(
                    "Failed to create Google GenAI embedding model '" + modelId + "'", exception);
        }
        return new GoogleGenAiTextEmbeddingAutoConfiguration()
                .googleGenAiTextEmbedding(
                        connectionDetails,
                        embeddingProperties,
                        context.provider(RetryTemplate.class),
                        context.provider(ObservationRegistry.class),
                        context.provider(EmbeddingModelObservationConvention.class));
    }
}
