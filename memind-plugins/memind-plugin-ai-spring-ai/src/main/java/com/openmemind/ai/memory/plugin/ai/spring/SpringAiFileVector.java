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

import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * User-facing helper for creating a file-backed Spring AI {@link MemoryVector}.
 */
public final class SpringAiFileVector {

    private SpringAiFileVector() {}

    public static MemoryVector file(String path, EmbeddingModel embeddingModel) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        return file(Path.of(path), embeddingModel);
    }

    public static MemoryVector file(Path path, EmbeddingModel embeddingModel) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(embeddingModel, "embeddingModel");
        return new SpringAiMemoryVector(
                new FileSimpleVectorStore(embeddingModel, path), embeddingModel);
    }
}
