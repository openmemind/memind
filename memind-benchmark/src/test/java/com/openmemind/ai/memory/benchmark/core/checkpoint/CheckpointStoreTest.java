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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckpointStoreTest {

    @TempDir Path tempDir;

    @Test
    void markCompletedPersistsAndReloads() {
        CheckpointStore store =
                new CheckpointStore(new ObjectMapper(), tempDir.resolve("checkpoint.json"));

        store.markCompleted("ADD", "user-1");
        store.markCompleted("EVALUATE", "question-1");
        store.flush();

        CheckpointStore reloaded =
                new CheckpointStore(new ObjectMapper(), tempDir.resolve("checkpoint.json"));

        assertThat(reloaded.isCompleted("ADD", "user-1")).isTrue();
        assertThat(reloaded.isCompleted("EVALUATE", "question-1")).isTrue();
        assertThat(reloaded.getCompleted("ADD")).containsExactly("user-1");
    }
}
