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
package com.openmemind.ai.memory.server.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.MemindServerApplication;
import com.openmemind.ai.memory.server.support.NoopRuntimeTestConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = {MemindServerApplication.class, NoopRuntimeTestConfiguration.class})
class OpenApiDocumentationTest {

    private static final Path DB_PATH =
            Path.of("target", "memind-server-openapi-" + UUID.randomUUID() + ".db")
                    .toAbsolutePath();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    private WebApplicationContext webApplicationContext;

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
    void setUp() throws IOException {
        Files.createDirectories(DB_PATH.getParent());
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterAll
    static void cleanUpDatabase() throws IOException {
        Files.deleteIfExists(DB_PATH);
    }

    @Test
    void exposesOpenApiJsonForOpenAndAdminEndpoints() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.openapi").exists())
                        .andExpect(jsonPath("$.info.title").value("Memind Server API"))
                        .andExpect(jsonPath("$.servers").doesNotExist())
                        .andExpect(jsonPath("$.paths['/open/v1/health']").exists())
                        .andExpect(
                                jsonPath("$.paths['/open/v1/health'].get.tags[0]").value("memory"))
                        .andExpect(
                                jsonPath("$.paths['/open/v1/health'].get.summary").value("Health"))
                        .andExpect(jsonPath("$.paths['/open/v1/memory/async/extract']").exists())
                        .andExpect(
                                jsonPath(
                                                "$.paths['/open/v1/memory/async/extract']"
                                                        + ".post.tags[0]")
                                        .value("memory"))
                        .andExpect(
                                jsonPath(
                                                "$.paths['/open/v1/memory/async/extract']"
                                                        + ".post.summary")
                                        .value("Extract Memory Async"))
                        .andExpect(
                                jsonPath(
                                                "$.paths['/open/v1/memory/async/add-message']"
                                                        + ".post.operationId")
                                        .value("addMessageAsync"))
                        .andExpect(
                                jsonPath(
                                                "$.paths['/open/v1/memory/async/add-message']"
                                                        + ".post.x-mint.metadata.title")
                                        .value("Add Message Async"))
                        .andExpect(
                                jsonPath(
                                                "$.paths['/open/v1/memory/async/add-message']"
                                                        + ".post.x-mint.metadata.sidebarTitle")
                                        .value("Add Message Async"))
                        .andExpect(
                                jsonPath(
                                                "$.paths['/open/v1/memory/async/add-message']"
                                                        + ".post.x-mint.href")
                                        .value("/api-reference/memory/add-message-async"))
                        .andExpect(jsonPath("$.paths['/admin/v1/items']").exists())
                        .andExpect(
                                jsonPath("$.paths['/admin/v1/items'].get.tags[0]").value("admin"))
                        .andExpect(
                                jsonPath("$.paths['/admin/v1/items'].get.summary")
                                        .value("List Memory Items"))
                        .andExpect(
                                jsonPath("$.paths['/admin/v1/items'].get.x-mint.href")
                                        .value(
                                                "/api-reference/admin/memory-items/list-memory-items"))
                        .andExpect(jsonPath("$.components.schemas.SuccessResult").exists())
                        .andExpect(jsonPath("$.components.schemas.ErrorResult").exists())
                        .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        writeOpenApiJsonIfRequested(root);
        assertThat(root.path("paths").size()).isEqualTo(37);
        assertThat(countOperations(root.path("paths"))).isGreaterThanOrEqualTo(40);
        assertThat(tagNames(root.path("tags"))).containsExactly("memory", "admin");
        assertThat(root.toString()).doesNotContain("controller", "http://localhost:8366");
    }

    private void writeOpenApiJsonIfRequested(JsonNode root) throws IOException {
        String outputPath = System.getProperty("memind.openapi.output");
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }

        Path output = Path.of(outputPath).toAbsolutePath().normalize();
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), root);
    }

    private static int countOperations(JsonNode paths) {
        int count = 0;
        var pathIterator = paths.properties().iterator();
        while (pathIterator.hasNext()) {
            JsonNode path = pathIterator.next().getValue();
            var operationIterator = path.properties().iterator();
            while (operationIterator.hasNext()) {
                String method = operationIterator.next().getKey();
                if (isHttpMethod(method)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static java.util.List<String> tagNames(JsonNode tags) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (JsonNode tag : tags.values()) {
            names.add(tag.path("name").asString());
        }
        return names;
    }

    private static boolean isHttpMethod(String method) {
        return switch (method) {
            case "get", "post", "put", "patch", "delete", "head", "options", "trace" -> true;
            default -> false;
        };
    }
}
