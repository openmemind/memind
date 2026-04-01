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
package com.openmemind.ai.memory.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.support.NoopRuntimeTestConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {MemindServerApplication.class, NoopRuntimeTestConfiguration.class})
class MemindServerSqliteBootstrapTest {

    private static final Path TEST_ROOT =
            Path.of("target", "memind-server-sqlite-bootstrap-" + UUID.randomUUID())
                    .toAbsolutePath();

    private static final Path DB_PATH = TEST_ROOT.resolve("data/memind-server.db");

    static {
        try {
            Files.createDirectories(TEST_ROOT.getParent());
            deleteRecursively(TEST_ROOT);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to prepare sqlite bootstrap test path", exception);
        }
    }

    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.main.web-application-type", () -> "servlet");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH);
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("memind.store.init-schema", () -> "true");
        registry.add(
                "spring.autoconfigure.exclude",
                () -> NoopRuntimeTestConfiguration.SPRING_AI_AUTOCONFIG_EXCLUDES);
    }

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterAll
    static void cleanUp() throws IOException {
        deleteRecursively(TEST_ROOT);
    }

    @Test
    void startupCreatesMissingSqliteParentDirectory() {
        assertThat(Files.isDirectory(DB_PATH.getParent())).isTrue();
        assertThat(Files.exists(DB_PATH)).isTrue();
    }

    @Test
    void configEndpointWorksWhenSqliteParentDirectoryWasMissing() throws Exception {
        mockMvc.perform(get("/admin/v1/config/memory-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(MemindServerSqliteBootstrapTest::delete);
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete test path " + path, exception);
        }
    }
}
