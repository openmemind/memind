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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

@DisplayName("Multi embedding model")
class MultiEmbeddingModelTest {

    @Test
    @DisplayName("exposes configured embedding models by model id")
    void exposesConfiguredEmbeddingModelsByModelId() {
        RecordingEmbeddingModel defaultModel = new RecordingEmbeddingModel("default");
        RecordingEmbeddingModel vectorModel = new RecordingEmbeddingModel("vector");
        Map<String, EmbeddingModel> embeddingModels = new LinkedHashMap<>();
        embeddingModels.put("default", defaultModel);
        embeddingModels.put("vector", vectorModel);

        MultiEmbeddingModel multiEmbeddingModel =
                new MultiEmbeddingModel("default", embeddingModels);

        assertThat(multiEmbeddingModel.getDefaultEmbeddingModelId()).isEqualTo("default");
        assertThat(multiEmbeddingModel.getDefaultEmbeddingModel()).isSameAs(defaultModel);
        assertThat(multiEmbeddingModel.getEmbeddingModel("vector")).isSameAs(vectorModel);
        assertThat(multiEmbeddingModel.getEmbeddingModels())
                .containsExactlyEntriesOf(embeddingModels)
                .isNotSameAs(embeddingModels);
        assertThatThrownBy(() -> multiEmbeddingModel.getEmbeddingModels().put("other", vectorModel))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("delegates EmbeddingModel calls to the default embedding model")
    void delegatesEmbeddingModelCallsToDefaultEmbeddingModel() {
        RecordingEmbeddingModel defaultModel = new RecordingEmbeddingModel("default");
        RecordingEmbeddingModel vectorModel = new RecordingEmbeddingModel("vector");
        MultiEmbeddingModel multiEmbeddingModel =
                new MultiEmbeddingModel(
                        "default", Map.of("default", defaultModel, "vector", vectorModel));
        EmbeddingRequest request =
                new EmbeddingRequest(List.of("hello"), EmbeddingOptions.builder().build());
        Document document = new Document("hello");

        EmbeddingResponse response = multiEmbeddingModel.call(request);
        float[] documentEmbedding = multiEmbeddingModel.embed(document);

        assertThat(response).isSameAs(defaultModel.callResponse);
        assertThat(documentEmbedding).isSameAs(defaultModel.documentEmbedding);
        assertThat(multiEmbeddingModel.dimensions()).isEqualTo(defaultModel.dimensions);
        assertThat(defaultModel.callRequests).containsExactly(request);
        assertThat(defaultModel.documents).containsExactly(document);
        assertThat(defaultModel.dimensionsCalls).isEqualTo(1);
        assertThat(vectorModel.callRequests).isEmpty();
        assertThat(vectorModel.documents).isEmpty();
        assertThat(vectorModel.dimensionsCalls).isZero();
    }

    @Test
    @DisplayName("fails clearly when a requested embedding model id is missing")
    void failsClearlyWhenRequestedEmbeddingModelIdIsMissing() {
        MultiEmbeddingModel multiEmbeddingModel =
                new MultiEmbeddingModel(
                        "default", Map.of("default", new RecordingEmbeddingModel("default")));

        assertThatThrownBy(() -> multiEmbeddingModel.getEmbeddingModel("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("fails clearly when the default embedding model id is missing")
    void failsClearlyWhenDefaultEmbeddingModelIdIsMissing() {
        assertThatThrownBy(
                        () ->
                                new MultiEmbeddingModel(
                                        "default",
                                        Map.of("vector", new RecordingEmbeddingModel("vector"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default");
    }

    private static final class RecordingEmbeddingModel implements EmbeddingModel {

        private final EmbeddingResponse callResponse;

        private final float[] documentEmbedding;

        private final int dimensions;

        private final List<EmbeddingRequest> callRequests = new java.util.ArrayList<>();

        private final List<Document> documents = new java.util.ArrayList<>();

        private int dimensionsCalls;

        private RecordingEmbeddingModel(String modelId) {
            this.callResponse =
                    new EmbeddingResponse(
                            List.of(new Embedding(new float[] {modelId.length()}, 0)));
            this.documentEmbedding = new float[] {modelId.length(), modelId.length() + 1};
            this.dimensions = modelId.length();
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            callRequests.add(request);
            return callResponse;
        }

        @Override
        public float[] embed(Document document) {
            documents.add(document);
            return documentEmbedding;
        }

        @Override
        public int dimensions() {
            dimensionsCalls++;
            return dimensions;
        }
    }
}
