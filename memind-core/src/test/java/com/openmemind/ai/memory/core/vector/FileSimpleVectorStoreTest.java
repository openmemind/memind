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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;

@DisplayName("FileSimpleVectorStore")
class FileSimpleVectorStoreTest {

    @TempDir Path tempDir;

    private EmbeddingModel embeddingModel;
    private Path vectorFile;

    @BeforeEach
    void setUp() {
        vectorFile = tempDir.resolve("vectors.json");
        embeddingModel = new FakeEmbeddingModel();
    }

    @Nested
    @DisplayName("Batch embedding")
    class BatchEmbedding {

        @Test
        @DisplayName("doAdd should vectorize all documents in batch at once")
        void shouldBatchEmbedAllDocuments() {
            var store = new FileSimpleVectorStore(embeddingModel, vectorFile);
            var docs =
                    List.of(
                            new Document("id1", "hello world", Map.of()),
                            new Document("id2", "foo bar", Map.of()));

            store.add(docs);

            var results =
                    store.similaritySearch(SearchRequest.builder().query("hello").topK(10).build());
            assertThat(results).hasSize(2);
            assertThat(((FakeEmbeddingModel) embeddingModel).batchCallCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("File persistence")
    class FilePersistence {

        @Test
        @DisplayName("add should automatically write to file")
        void shouldPersistAfterAdd() throws IOException {
            var store = new FileSimpleVectorStore(embeddingModel, vectorFile);
            store.add(List.of(new Document("id1", "text", Map.of())));

            assertThat(Files.exists(vectorFile)).isTrue();
            assertThat(Files.size(vectorFile)).isGreaterThan(0);
        }

        @Test
        @DisplayName("delete should automatically write to file")
        void shouldPersistAfterDelete() throws IOException {
            var store = new FileSimpleVectorStore(embeddingModel, vectorFile);
            store.add(List.of(new Document("id1", "text", Map.of())));
            long sizeAfterAdd = Files.size(vectorFile);

            store.delete(List.of("id1"));

            // The file should still exist but the content has changed
            assertThat(Files.exists(vectorFile)).isTrue();
            assertThat(Files.size(vectorFile)).isNotEqualTo(sizeAfterAdd);
        }

        @Test
        @DisplayName("New instance should load existing data from file")
        void shouldLoadExistingDataOnStartup() {
            var store1 = new FileSimpleVectorStore(embeddingModel, vectorFile);
            store1.add(List.of(new Document("id1", "hello world", Map.of())));

            // Creating a new instance should automatically load
            var store2 = new FileSimpleVectorStore(embeddingModel, vectorFile);
            var results =
                    store2.similaritySearch(
                            SearchRequest.builder().query("hello").topK(10).build());
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getId()).isEqualTo("id1");
        }

        @Test
        @DisplayName("Should not throw an error when the file does not exist")
        void shouldHandleMissingFile() {
            var store = new FileSimpleVectorStore(embeddingModel, vectorFile);
            var results =
                    store.similaritySearch(
                            SearchRequest.builder().query("anything").topK(10).build());
            assertThat(results).isEmpty();
        }
    }
}
