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
package com.openmemind.ai.memory.core.resource;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Local filesystem-backed {@link ResourceStore}.
 */
public class LocalFileResourceStore implements ResourceStore {

    private final Path baseDir;

    public LocalFileResourceStore(Path baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir").toAbsolutePath().normalize();
    }

    @Override
    public Mono<ResourceRef> store(
            MemoryId memoryId,
            String fileName,
            byte[] data,
            String mimeType,
            Map<String, Object> metadata) {
        return Mono.fromCallable(
                () -> {
                    validateFileName(fileName);
                    byte[] bytes = data == null ? new byte[0] : data.clone();
                    String resourceId = UUID.randomUUID().toString();
                    Instant now = Instant.now();
                    Path resourcePath =
                            baseDir.resolve(memoryId.toIdentifier())
                                    .resolve(resourceId)
                                    .resolve(fileName);
                    Path normalized = resourcePath.toAbsolutePath().normalize();
                    if (!normalized.startsWith(baseDir)) {
                        throw new IllegalArgumentException("fileName must stay within baseDir");
                    }
                    Files.createDirectories(normalized.getParent());
                    Files.write(normalized, bytes);
                    return new ResourceRef(
                            resourceId,
                            memoryId.toIdentifier(),
                            fileName,
                            mimeType,
                            normalized.toUri().toString(),
                            bytes.length,
                            now);
                });
    }

    @Override
    public Mono<byte[]> retrieve(ResourceRef ref) {
        return Mono.fromCallable(() -> Files.readAllBytes(toPath(ref)));
    }

    @Override
    public Mono<Void> delete(ResourceRef ref) {
        return Mono.fromRunnable(
                        () -> {
                            Path path = toPath(ref);
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new IllegalStateException(
                                        "Failed to delete resource " + path, e);
                            }
                        })
                .then();
    }

    @Override
    public Mono<Boolean> exists(ResourceRef ref) {
        return Mono.fromCallable(() -> Files.exists(toPath(ref)));
    }

    private Path toPath(ResourceRef ref) {
        return Path.of(URI.create(ref.storageUri())).toAbsolutePath().normalize();
    }

    private void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        Path path = Path.of(fileName);
        if (path.isAbsolute() || fileName.contains("..")) {
            throw new IllegalArgumentException("fileName must be a safe relative path");
        }
    }
}
