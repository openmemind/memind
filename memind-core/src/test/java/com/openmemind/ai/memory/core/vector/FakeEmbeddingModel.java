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
package com.openmemind.ai.memory.core.vector;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Fake EmbeddingModel that returns a deterministic vector of fixed dimensions for testing.
 */
class FakeEmbeddingModel implements EmbeddingModel {
    int batchCallCount = 0;

    @Override
    public float[] embed(String text) {
        // Generate a deterministic vector based on the text hashCode
        var hash = text.hashCode();
        return new float[] {
            (hash & 0xFF) / 255f, ((hash >> 8) & 0xFF) / 255f, ((hash >> 16) & 0xFF) / 255f
        };
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        batchCallCount++;
        return texts.stream().map(this::embed).toList();
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        var instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embed(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return 3;
    }
}
