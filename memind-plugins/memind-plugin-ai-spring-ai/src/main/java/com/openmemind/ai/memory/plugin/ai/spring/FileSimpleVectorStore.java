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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStoreContent;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * SimpleVectorStore that supports batch embedding and file persistence.
 *
 * <p>Override doAdd, using EmbeddingModel.embed(List&lt;String&gt;) to vectorize in bulk at once;
 * automatically saves to file after each doAdd / doDelete; automatically loads existing data from
 * file on startup.
 */
public class FileSimpleVectorStore extends SimpleVectorStore {

    private static final Logger log = LoggerFactory.getLogger(FileSimpleVectorStore.class);
    private static final ConcurrentHashMap<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();
    private static final ObjectWriter VECTOR_STORE_WRITER =
            JsonMapper.builder().findAndAddModules().build().writerWithDefaultPrettyPrinter();

    private final Path filePath;
    private final Object persistLock;

    public FileSimpleVectorStore(EmbeddingModel embeddingModel, Path filePath) {
        super(SimpleVectorStore.builder(embeddingModel));
        this.filePath =
                Objects.requireNonNull(filePath, "filePath is required")
                        .toAbsolutePath()
                        .normalize();
        this.persistLock = FILE_LOCKS.computeIfAbsent(this.filePath, ignored -> new Object());
        if (Files.exists(this.filePath)) {
            log.info("Loading vector data from file: {}", this.filePath);
            load(this.filePath.toFile());
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

        synchronized (persistLock) {
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

            persistLocked();
        }
    }

    @Override
    public void doDelete(List<String> idList) {
        synchronized (persistLock) {
            super.doDelete(idList);
            persistLocked();
        }
    }

    private void persistLocked() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writeStoreSnapshot(filePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist vector store file: " + filePath, e);
        }
    }

    @Override
    public void save(File file) {
        try {
            writeStoreSnapshot(file.toPath().toAbsolutePath().normalize());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save vector store file: " + file, e);
        }
    }

    private void writeStoreSnapshot(Path targetPath) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile =
                Files.createTempFile(
                        parent != null ? parent : targetPath.toAbsolutePath().getParent(),
                        targetPath.getFileName().toString(),
                        ".tmp");
        boolean replaceSucceeded = false;
        try {
            VECTOR_STORE_WRITER.writeValue(tempFile.toFile(), this.store);
            moveAtomicallyIfPossible(tempFile, targetPath);
            replaceSucceeded = true;
        } finally {
            if (!replaceSucceeded) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private void moveAtomicallyIfPossible(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
