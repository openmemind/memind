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
package com.openmemind.ai.memory.benchmark.core.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;

public final class CheckpointStore {

    private final ObjectMapper objectMapper;
    private final Path checkpointFile;
    private final Object monitor = new Object();
    private CheckpointState state;

    public CheckpointStore(ObjectMapper objectMapper, Path checkpointFile) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.checkpointFile = Objects.requireNonNull(checkpointFile, "checkpointFile");
        this.state = load();
    }

    public void markCompleted(String stage, String itemId) {
        synchronized (monitor) {
            state.markCompleted(stage, itemId);
        }
    }

    public boolean isCompleted(String stage, String itemId) {
        synchronized (monitor) {
            return state.isCompleted(stage, itemId);
        }
    }

    public Set<String> getCompleted(String stage) {
        synchronized (monitor) {
            return state.getCompleted(stage);
        }
    }

    public void flush() {
        synchronized (monitor) {
            try {
                Files.createDirectories(checkpointFile.toAbsolutePath().normalize().getParent());
                Path tempFile =
                        checkpointFile.resolveSibling(checkpointFile.getFileName() + ".tmp");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), state);
                Files.move(
                        tempFile,
                        checkpointFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Failed to flush checkpoint to " + checkpointFile, exception);
            }
        }
    }

    private CheckpointState load() {
        if (!Files.exists(checkpointFile)) {
            return new CheckpointState();
        }
        try {
            return objectMapper.readValue(checkpointFile.toFile(), CheckpointState.class);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load checkpoint from " + checkpointFile, exception);
        }
    }
}
