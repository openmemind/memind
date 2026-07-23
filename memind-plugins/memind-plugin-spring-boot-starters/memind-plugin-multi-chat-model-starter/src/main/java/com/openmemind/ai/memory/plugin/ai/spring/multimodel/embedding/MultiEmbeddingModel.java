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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.embedding;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

public final class MultiEmbeddingModel implements EmbeddingModel {

    private final String defaultEmbeddingModelId;

    private final EmbeddingModel defaultEmbeddingModel;

    private final Map<String, EmbeddingModel> embeddingModels;

    public MultiEmbeddingModel(
            String defaultEmbeddingModelId, Map<String, EmbeddingModel> embeddingModels) {
        this.defaultEmbeddingModelId = requireModelId(defaultEmbeddingModelId);
        this.embeddingModels = copyEmbeddingModels(embeddingModels);
        this.defaultEmbeddingModel = this.embeddingModels.get(this.defaultEmbeddingModelId);
        if (this.defaultEmbeddingModel == null) {
            throw missingEmbeddingModel(this.defaultEmbeddingModelId);
        }
    }

    public String getDefaultEmbeddingModelId() {
        return defaultEmbeddingModelId;
    }

    public EmbeddingModel getDefaultEmbeddingModel() {
        return defaultEmbeddingModel;
    }

    public EmbeddingModel getEmbeddingModel(String modelId) {
        String requiredModelId = requireModelId(modelId);
        EmbeddingModel embeddingModel = embeddingModels.get(requiredModelId);
        if (embeddingModel == null) {
            throw missingEmbeddingModel(requiredModelId);
        }
        return embeddingModel;
    }

    public Map<String, EmbeddingModel> getEmbeddingModels() {
        return embeddingModels;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return defaultEmbeddingModel.call(request);
    }

    @Override
    public float[] embed(Document document) {
        return defaultEmbeddingModel.embed(document);
    }

    @Override
    public int dimensions() {
        return defaultEmbeddingModel.dimensions();
    }

    private static Map<String, EmbeddingModel> copyEmbeddingModels(
            Map<String, EmbeddingModel> embeddingModels) {
        if (embeddingModels == null || embeddingModels.isEmpty()) {
            throw new IllegalArgumentException("embeddingModels must not be empty");
        }
        Map<String, EmbeddingModel> copiedEmbeddingModels = new LinkedHashMap<>();
        embeddingModels.forEach(
                (modelId, embeddingModel) ->
                        copiedEmbeddingModels.put(
                                requireModelId(modelId),
                                Objects.requireNonNull(
                                        embeddingModel,
                                        "embeddingModel must not be null for model id '"
                                                + modelId
                                                + "'")));
        return Collections.unmodifiableMap(copiedEmbeddingModels);
    }

    private static String requireModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("embedding model id must not be blank");
        }
        return modelId;
    }

    private static IllegalArgumentException missingEmbeddingModel(String modelId) {
        return new IllegalArgumentException(
                "No embedding model configured with id '" + modelId + "'");
    }
}
