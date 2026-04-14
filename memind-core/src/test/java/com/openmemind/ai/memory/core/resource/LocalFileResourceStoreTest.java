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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileResourceStoreTest {

    @TempDir Path tempDir;

    @Test
    void storeRetrieveExistsAndDeleteShouldRoundTrip() throws Exception {
        var store = new LocalFileResourceStore(tempDir);

        var ref =
                store.store(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                "report.pdf",
                                "hello".getBytes(StandardCharsets.UTF_8),
                                "application/pdf",
                                Map.of("sourceUri", "file:///tmp/report.pdf"))
                        .block();

        assertThat(ref).isNotNull();
        assertThat(ref.fileName()).isEqualTo("report.pdf");
        assertThat(ref.mimeType()).isEqualTo("application/pdf");
        assertThat(store.exists(ref).block()).isTrue();
        assertThat(store.retrieve(ref).block()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));

        Path storedPath = Path.of(java.net.URI.create(ref.storageUri()));
        assertThat(storedPath).exists();
        assertThat(storedPath).startsWith(tempDir);

        store.delete(ref).block();

        assertThat(store.exists(ref).block()).isFalse();
        assertThat(Files.exists(storedPath)).isFalse();
    }

    @Test
    void shouldRejectPathTraversalAndAbsolutePaths() {
        var store = new LocalFileResourceStore(tempDir);

        assertThatThrownBy(
                        () ->
                                store.store(
                                                DefaultMemoryId.of("user-1", "agent-1"),
                                                "../report.pdf",
                                                new byte[] {1},
                                                "application/pdf",
                                                Map.of())
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileName");

        assertThatThrownBy(
                        () ->
                                store.store(
                                                DefaultMemoryId.of("user-1", "agent-1"),
                                                "/tmp/report.pdf",
                                                new byte[] {1},
                                                "application/pdf",
                                                Map.of())
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileName");
    }
}
