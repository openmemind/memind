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

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

@DisplayName("SpringAiMemoryVector")
class SpringAiMemoryVectorTest {

    @TempDir Path tempDir;

    private SpringAiMemoryVector memoryVector;
    private MemoryId memoryId;

    @BeforeEach
    void setUp() {
        memoryId = DefaultMemoryId.of("user1", "agent1");
        var embeddingModel = new FakeEmbeddingModel();
        var vectorStore =
                new FileSimpleVectorStore(embeddingModel, tempDir.resolve("test-vectors.json"));
        memoryVector = new SpringAiMemoryVector(vectorStore, embeddingModel);
    }

    @Nested
    @DisplayName("Store and Search")
    class StoreAndSearch {

        @Test
        @DisplayName("store should return vectorId")
        void storeShouldReturnVectorId() {
            StepVerifier.create(memoryVector.store(memoryId, "hello world", Map.of()))
                    .assertNext(vectorId -> assertThat(vectorId).isNotBlank())
                    .verifyComplete();
        }

        @Test
        @DisplayName("storeBatch should return all vectorId and be searchable")
        void storeBatchShouldReturnAllVectorIds() {
            StepVerifier.create(
                            memoryVector
                                    .storeBatch(
                                            memoryId,
                                            List.of("first document", "second document"),
                                            List.of(Map.of(), Map.of()))
                                    .flatMap(
                                            vectorIds -> {
                                                assertThat(vectorIds).hasSize(2);
                                                return memoryVector
                                                        .search(memoryId, "first", 10)
                                                        .collectList();
                                            }))
                    .assertNext(results -> assertThat(results).isNotEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("should be able to search after storing")
        void shouldFindStoredDocument() {
            var vectorId = memoryVector.store(memoryId, "hello world", Map.of()).block();
            assertThat(vectorId).isNotBlank();

            StepVerifier.create(memoryVector.search(memoryId, "hello", 10).collectList())
                    .assertNext(
                            results -> {
                                assertThat(results).isNotEmpty();
                                assertThat(results.getFirst().text()).isEqualTo("hello world");
                            })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("should not find after delete")
        void shouldNotFindAfterDelete() {
            var vectorId = memoryVector.store(memoryId, "to be deleted", Map.of()).block();
            memoryVector.delete(memoryId, vectorId).block();

            StepVerifier.create(memoryVector.search(memoryId, "deleted", 10).collectList())
                    .assertNext(results -> assertThat(results).isEmpty())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Embedding")
    class Embedding {

        @Test
        @DisplayName("embed should return vector")
        void embedShouldReturnVector() {
            StepVerifier.create(memoryVector.embed("test text"))
                    .assertNext(vector -> assertThat(vector).isNotEmpty())
                    .verifyComplete();
        }
    }
}
