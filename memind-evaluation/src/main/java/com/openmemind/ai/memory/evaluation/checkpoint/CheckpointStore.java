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
package com.openmemind.ai.memory.evaluation.checkpoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.evaluation.adapter.model.SearchResult;
import com.openmemind.ai.memory.evaluation.pipeline.Stage;
import com.openmemind.ai.memory.evaluation.pipeline.model.AnswerResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluation checkpoint persistence storage, responsible for reading and writing JSON files for checkpoint / search / answer stages
 *
 */
public class CheckpointStore {
    private static final Logger log = LoggerFactory.getLogger(CheckpointStore.class);
    private static final String CHECKPOINT_FILE = "checkpoint.json";
    private static final String SEARCH_CHECKPOINT_FILE = "search_checkpoint.json";
    private static final String ANSWER_CHECKPOINT_FILE = "answer_checkpoint.json";
    private static final String SEARCH_RESULTS_FILE = "search_results.json";
    private static final String ANSWER_RESULTS_FILE = "answer_results.json";

    private final ObjectMapper mapper;
    private final Path baseDir;

    public CheckpointStore(ObjectMapper mapper, Path baseDir) {
        this.mapper = mapper;
        this.baseDir = baseDir;
    }

    private Path checkpointPath(String dataset, String adapter, String runName) {
        return baseDir.resolve(dataset + "-" + adapter + "-" + runName).resolve(CHECKPOINT_FILE);
    }

    public CheckpointState load(String dataset, String adapter, String runName) {
        Path path = checkpointPath(dataset, adapter, runName);
        if (Files.exists(path)) {
            try {
                CheckpointState state = mapper.readValue(path.toFile(), CheckpointState.class);
                log.info(
                        "Loaded checkpoint: add={}, stages={}",
                        state.completedAddConvIds().size(),
                        state.completedStages().size());
                return state;
            } catch (IOException e) {
                log.warn("Failed to read checkpoint, starting fresh: {}", e.getMessage());
            }
        }
        return new CheckpointState(dataset, adapter, runName);
    }

    public void save(CheckpointState state) {
        Path path =
                checkpointPath(state.getDatasetName(), state.getAdapterName(), state.getRunName());
        saveFile(path, state);
    }

    public Path runDir(String dataset, String adapter, String runName) {
        return baseDir.resolve(dataset + "-" + adapter + "-" + runName);
    }

    // ── Stage-level checkpoint ──────────────────────────────────────────

    public boolean isStageCompleted(Path runDir, Stage stage) {
        Path path = runDir.resolve(CHECKPOINT_FILE);
        if (!Files.exists(path)) {
            return false;
        }
        try {
            CheckpointState state = mapper.readValue(path.toFile(), CheckpointState.class);
            return state.isStageCompleted(stage.name());
        } catch (IOException e) {
            log.warn("Failed to read checkpoint: {}", e.getMessage());
            return false;
        }
    }

    public void markStageCompleted(Path runDir, Stage stage) {
        Path path = runDir.resolve(CHECKPOINT_FILE);
        CheckpointState state;
        if (Files.exists(path)) {
            try {
                state = mapper.readValue(path.toFile(), CheckpointState.class);
            } catch (IOException e) {
                log.warn("Failed to read checkpoint, creating new: {}", e.getMessage());
                state = new CheckpointState();
            }
        } else {
            state = new CheckpointState();
        }
        state.markStageCompleted(stage.name());
        saveFile(path, state);
    }

    // ── Search granular checkpoint (search_checkpoint.json) ─────────────

    public Map<String, List<SearchResult>> loadSearchCheckpoint(Path runDir) {
        Path path = runDir.resolve(SEARCH_CHECKPOINT_FILE);
        if (Files.exists(path)) {
            try {
                return mapper.readValue(
                        path.toFile(), new TypeReference<Map<String, List<SearchResult>>>() {});
            } catch (IOException e) {
                log.warn("Failed to read search checkpoint: {}", e.getMessage());
            }
        }
        return Map.of();
    }

    public void saveSearchCheckpoint(Path runDir, Map<String, List<SearchResult>> data) {
        saveFile(runDir.resolve(SEARCH_CHECKPOINT_FILE), data);
    }

    public void deleteSearchCheckpoint(Path runDir) {
        deleteFile(runDir.resolve(SEARCH_CHECKPOINT_FILE));
    }

    // ── Answer granular checkpoint (answer_checkpoint.json) ─────────────

    public Map<String, AnswerResult> loadAnswerCheckpoint(Path runDir) {
        Path path = runDir.resolve(ANSWER_CHECKPOINT_FILE);
        if (Files.exists(path)) {
            try {
                return mapper.readValue(
                        path.toFile(), new TypeReference<Map<String, AnswerResult>>() {});
            } catch (IOException e) {
                log.warn("Failed to read answer checkpoint: {}", e.getMessage());
            }
        }
        return Map.of();
    }

    public void saveAnswerCheckpoint(Path runDir, Map<String, AnswerResult> data) {
        saveFile(runDir.resolve(ANSWER_CHECKPOINT_FILE), data);
    }

    public void deleteAnswerCheckpoint(Path runDir) {
        deleteFile(runDir.resolve(ANSWER_CHECKPOINT_FILE));
    }

    // ── Final result files ──────────────────────────────────────────────

    public List<SearchResult> loadSearchResults(Path runDir) {
        Path path = runDir.resolve(SEARCH_RESULTS_FILE);
        if (Files.exists(path)) {
            try {
                return mapper.readValue(path.toFile(), new TypeReference<List<SearchResult>>() {});
            } catch (IOException e) {
                log.warn("Failed to read search results: {}", e.getMessage());
            }
        }
        return List.of();
    }

    public void saveSearchResults(Path runDir, List<SearchResult> results) {
        saveFile(runDir.resolve(SEARCH_RESULTS_FILE), results);
    }

    public List<AnswerResult> loadAnswerResults(Path runDir) {
        Path path = runDir.resolve(ANSWER_RESULTS_FILE);
        if (Files.exists(path)) {
            try {
                return mapper.readValue(path.toFile(), new TypeReference<List<AnswerResult>>() {});
            } catch (IOException e) {
                log.warn("Failed to read answer results: {}", e.getMessage());
            }
        }
        return List.of();
    }

    public void saveAnswerResults(Path runDir, List<AnswerResult> results) {
        saveFile(runDir.resolve(ANSWER_RESULTS_FILE), results);
    }

    // ── Internal helpers ────────────────────────────────────────────────

    // Write to a temporary file first and then atomically move it to prevent data corruption in
    // case of a crash during writing
    private void saveFile(Path path, Object data) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            mapper.writeValue(tmp.toFile(), data);
            Files.move(
                    tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Failed to save file {}: {}", path, e.getMessage());
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete file {}: {}", path, e.getMessage());
        }
    }
}
