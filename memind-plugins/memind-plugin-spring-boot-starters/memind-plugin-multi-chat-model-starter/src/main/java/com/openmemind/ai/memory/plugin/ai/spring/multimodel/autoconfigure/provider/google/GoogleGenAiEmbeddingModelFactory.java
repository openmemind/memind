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
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.MultiAiGoogleGenAiEmbeddingProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiEmbeddingModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiEmbeddingModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiModelProviderContext;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.ProviderPropertyMapper;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionProperties;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingProperties;
import org.springframework.core.retry.RetryTemplate;

public final class GoogleGenAiEmbeddingModelFactory
        implements MultiAiEmbeddingModelFactory<MultiAiGoogleGenAiEmbeddingProperties> {

    @Override
    public MultiAiEmbeddingModelProviderType providerType() {
        return MultiAiEmbeddingModelProviderType.GOOGLE;
    }

    @Override
    public EmbeddingModel createEmbeddingModel(
            String modelId,
            MultiAiGoogleGenAiEmbeddingProperties properties,
            MultiAiModelProviderContext context) {
        GoogleGenAiEmbeddingConnectionProperties connectionProperties =
                connectionProperties(properties);
        GoogleGenAiTextEmbeddingProperties embeddingProperties = embeddingProperties(properties);
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

    private static GoogleGenAiEmbeddingConnectionProperties connectionProperties(
            MultiAiGoogleGenAiEmbeddingProperties properties) {
        GoogleGenAiEmbeddingConnectionProperties target =
                new GoogleGenAiEmbeddingConnectionProperties();
        ProviderPropertyMapper.copyProperties(properties, target);
        return target;
    }

    private static GoogleGenAiTextEmbeddingProperties embeddingProperties(
            MultiAiGoogleGenAiEmbeddingProperties properties) {
        GoogleGenAiTextEmbeddingProperties target = new GoogleGenAiTextEmbeddingProperties();
        ProviderPropertyMapper.copyProperties(properties, target, "taskType");
        target.setTaskType(
                ProviderPropertyMapper.convert(properties.getTaskType(), TaskType.class));
        return target;
    }
}
