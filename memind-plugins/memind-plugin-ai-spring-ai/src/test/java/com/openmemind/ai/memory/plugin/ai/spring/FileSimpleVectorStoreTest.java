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
package com.openmemind.ai.memory.plugin.ai.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStoreContent;

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

            assertThat(Files.exists(vectorFile)).isTrue();
            assertThat(Files.size(vectorFile)).isNotEqualTo(sizeAfterAdd);
        }

        @Test
        @DisplayName("New instance should load existing data from file")
        void shouldLoadExistingDataOnStartup() {
            var store1 = new FileSimpleVectorStore(embeddingModel, vectorFile);
            store1.add(List.of(new Document("id1", "hello world", Map.of())));

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

        @Test
        @DisplayName("concurrent add should persist without serialization failures")
        void shouldPersistSafelyUnderConcurrentAdd() throws Exception {
            var store = new FileSimpleVectorStore(new SlowFakeEmbeddingModel(), vectorFile);

            // Make each save expensive enough that concurrent add operations overlap in persist().
            store.add(createDocs("warmup", 2_000, 20));

            try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
                List<Future<?>> futures = new ArrayList<>();
                for (int batch = 0; batch < 24; batch++) {
                    int batchNo = batch;
                    futures.add(
                            executor.submit(
                                    () -> store.add(createDocs("batch-" + batchNo, 50, 5))));
                }

                assertThatCode(
                                () -> {
                                    for (Future<?> future : futures) {
                                        future.get();
                                    }
                                })
                        .doesNotThrowAnyException();
            }

            assertThat(Files.exists(vectorFile)).isTrue();
            assertThat(Files.size(vectorFile)).isGreaterThan(0);
        }

        @Test
        @DisplayName("concurrent add should not mutate store while a save is in progress")
        void shouldNotMutateStoreDuringSave() throws Exception {
            var store = new TestableFileSimpleVectorStore(embeddingModel, vectorFile);
            var backingMap = new MutationTrackingMap();
            store.installStore(backingMap);

            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<?> first =
                        executor.submit(
                                () ->
                                        store.add(
                                                List.of(
                                                        new Document(
                                                                "id-1", "first-doc", Map.of()))));

                backingMap.awaitSerializationStart();

                Future<?> second =
                        executor.submit(
                                () ->
                                        store.add(
                                                List.of(
                                                        new Document(
                                                                "id-2", "second-doc", Map.of()))));

                assertThat(backingMap.awaitConcurrentMutation(300, TimeUnit.MILLISECONDS))
                        .isFalse();
                backingMap.finishSerialization();

                first.get();
                second.get();
            }

            assertThat(backingMap.wasMutatedDuringSerialization()).isFalse();
        }
    }

    private List<Document> createDocs(String prefix, int batchCount, int textRepeat) {
        List<Document> docs = new ArrayList<>(batchCount);
        String repeatedText = "x".repeat(textRepeat * 100);
        for (int i = 0; i < batchCount; i++) {
            docs.add(
                    new Document(
                            prefix + "-" + i, prefix + "-" + repeatedText + "-" + i, Map.of()));
        }
        return docs;
    }

    private static final class SlowFakeEmbeddingModel extends FakeEmbeddingModel {

        @Override
        public List<float[]> embed(List<String> texts) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return super.embed(texts);
        }
    }

    private static final class TestableFileSimpleVectorStore extends FileSimpleVectorStore {

        private TestableFileSimpleVectorStore(EmbeddingModel embeddingModel, Path filePath) {
            super(embeddingModel, filePath);
        }

        private void installStore(MutationTrackingMap store) {
            this.store = store;
        }
    }

    private static final class MutationTrackingMap
            extends java.util.concurrent.ConcurrentHashMap<String, SimpleVectorStoreContent> {

        private final CountDownLatch serializationStarted = new CountDownLatch(1);
        private final CountDownLatch serializationReleased = new CountDownLatch(1);
        private final CountDownLatch concurrentMutation = new CountDownLatch(1);
        private final AtomicBoolean serializing = new AtomicBoolean(false);
        private final AtomicBoolean mutatedDuringSerialization = new AtomicBoolean(false);

        @Override
        public SimpleVectorStoreContent put(String key, SimpleVectorStoreContent value) {
            if (serializing.get()) {
                mutatedDuringSerialization.set(true);
                concurrentMutation.countDown();
            }
            return super.put(key, value);
        }

        @Override
        public java.util.Set<Entry<String, SimpleVectorStoreContent>> entrySet() {
            var delegate = super.entrySet();
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<String, SimpleVectorStoreContent>> iterator() {
                    var iterator = delegate.iterator();
                    return new Iterator<>() {
                        private boolean started;

                        @Override
                        public boolean hasNext() {
                            blockSerializationOnce();
                            return iterator.hasNext();
                        }

                        @Override
                        public Entry<String, SimpleVectorStoreContent> next() {
                            blockSerializationOnce();
                            return iterator.next();
                        }

                        private void blockSerializationOnce() {
                            if (started) {
                                return;
                            }
                            started = true;
                            serializing.set(true);
                            serializationStarted.countDown();
                            try {
                                if (!serializationReleased.await(5, TimeUnit.SECONDS)) {
                                    throw new AssertionError("Timed out waiting to release save");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            } finally {
                                serializing.set(false);
                            }
                        }
                    };
                }

                @Override
                public int size() {
                    return delegate.size();
                }
            };
        }

        private void awaitSerializationStart() throws InterruptedException {
            assertThat(serializationStarted.await(5, TimeUnit.SECONDS)).isTrue();
        }

        private boolean awaitConcurrentMutation(long timeout, TimeUnit unit)
                throws InterruptedException {
            return concurrentMutation.await(timeout, unit);
        }

        private void finishSerialization() {
            serializationReleased.countDown();
        }

        private boolean wasMutatedDuringSerialization() {
            return mutatedDuringSerialization.get();
        }
    }
}
