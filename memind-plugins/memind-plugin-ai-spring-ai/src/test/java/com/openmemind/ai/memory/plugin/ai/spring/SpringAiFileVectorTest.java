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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringAiFileVectorTest {

    @TempDir Path tempDir;

    @Test
    void fileHelperCreatesMemoryVector() {
        var memoryVector =
                SpringAiFileVector.file(
                        tempDir.resolve("vector-store.json"), new FakeEmbeddingModel());

        assertThat(memoryVector).isInstanceOf(SpringAiMemoryVector.class);
    }

    @Test
    void fileHelperRejectsBlankPath() {
        assertThatThrownBy(() -> SpringAiFileVector.file(" ", new FakeEmbeddingModel()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("path must not be blank");
    }
}
