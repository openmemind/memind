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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStoreContent;

/**
 * SimpleVectorStore that supports batch embedding and file persistence.
 *
 * <p>Override doAdd, using EmbeddingModel.embed(List&lt;String&gt;) to vectorize in bulk at once;
 * automatically saves to file after each doAdd / doDelete; automatically loads existing data from
 * file on startup.
 */
public class FileSimpleVectorStore extends SimpleVectorStore {

    private static final Logger log = LoggerFactory.getLogger(FileSimpleVectorStore.class);

    private final Path filePath;
    private final Object persistLock = new Object();

    public FileSimpleVectorStore(EmbeddingModel embeddingModel, Path filePath) {
        super(SimpleVectorStore.builder(embeddingModel));
        this.filePath = Objects.requireNonNull(filePath, "filePath is required");
        if (Files.exists(filePath)) {
            log.info("Loading vector data from file: {}", filePath);
            load(filePath.toFile());
        }
    }

    @Override
    public void doAdd(List<Document> documents) {
        if (documents.isEmpty()) {
            return;
        }

        List<String> texts =
                documents.stream()
                        .map(doc -> Objects.requireNonNullElse(doc.getText(), ""))
                        .toList();

        log.info("Batch vectorizing {} documents", texts.size());
        List<float[]> embeddings = this.embeddingModel.embed(texts);

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            var content =
                    new SimpleVectorStoreContent(
                            doc.getId(),
                            Objects.requireNonNullElse(doc.getText(), ""),
                            doc.getMetadata(),
                            embeddings.get(i));
            this.store.put(doc.getId(), content);
        }

        persist();
    }

    @Override
    public void doDelete(List<String> idList) {
        super.doDelete(idList);
        persist();
    }

    private void persist() {
        synchronized (persistLock) {
            try {
                Path parent = filePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(
                        "Failed to create vector store directory: " + filePath.getParent(), e);
            }
            save(filePath.toFile());
        }
    }
}
