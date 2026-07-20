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
package com.openmemind.ai.memory.server.mcp;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.MemindServerApplication;
import com.openmemind.ai.memory.server.support.NoopRuntimeTestConfiguration;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class MemindMcpApplicationTest {

    private static final List<String> DEFAULT_TOOL_NAMES =
            List.of(
                    "memind_add_message",
                    "memind_commit",
                    "memind_compile_context",
                    "memind_extract_rawdata",
                    "memind_extract_text",
                    "memind_items_get",
                    "memind_items_search",
                    "memind_items_sources",
                    "memind_rawdata_get",
                    "memind_rawdata_search",
                    "memind_recent",
                    "memind_retrieve");

    private static final List<String> GOVERNANCE_TOOL_NAMES =
            List.of(
                    "memind_add_message",
                    "memind_commit",
                    "memind_compile_context",
                    "memind_extract_rawdata",
                    "memind_extract_text",
                    "memind_forget",
                    "memind_items_get",
                    "memind_items_search",
                    "memind_items_sources",
                    "memind_rawdata_get",
                    "memind_rawdata_search",
                    "memind_recent",
                    "memind_retrieve");

    @Nested
    @SpringBootTest(classes = {MemindServerApplication.class, NoopRuntimeTestConfiguration.class})
    class Enabled {

        private static final Path DB_PATH =
                Path.of("target", "memind-mcp-enabled-" + UUID.randomUUID() + ".db")
                        .toAbsolutePath();

        @Autowired private ApplicationContext applicationContext;
        @Autowired private WebApplicationContext webApplicationContext;

        @Autowired
        private ObjectProvider<List<McpStatelessServerFeatures.SyncToolSpecification>>
                syncToolSpecifications;

        @DynamicPropertySource
        static void registerProperties(DynamicPropertyRegistry registry) {
            prepareDatabasePath(DB_PATH);
            registry.add("spring.main.web-application-type", () -> "servlet");
            registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH);
            registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
            registry.add("memind.store.init-schema", () -> "true");
            registry.add("spring.ai.mcp.server.enabled", () -> "true");
            registry.add(
                    "spring.autoconfigure.exclude",
                    () -> NoopRuntimeTestConfiguration.SPRING_AI_AUTOCONFIG_EXCLUDES);
        }

        @Test
        void contextRegistersOnlyDefaultMemindMcpTools() {
            assertThat(applicationContext.getBeanNamesForType(MemindMcpToolService.class))
                    .hasSize(1);
            assertThat(applicationContext.getBeanNamesForType(MemindMcpGovernanceToolService.class))
                    .isEmpty();
            assertThat(applicationContext.getBeanNamesForType(McpStatelessSyncServer.class))
                    .hasSize(1);

            assertThat(toolNames()).containsExactlyElementsOf(DEFAULT_TOOL_NAMES);
        }

        @Test
        void toolsExposeFlatInputSchemas() {
            Map<String, List<String>> requiredFieldsByTool = requiredFieldsByDefaultTool();
            Map<String, List<String>> optionalFieldsByTool = optionalFieldsByDefaultTool();

            toolSpecifications()
                    .forEach(
                            spec -> {
                                var inputSchema = spec.tool().inputSchema();
                                var toolName = spec.tool().name();
                                var schemaProperties = schemaProperties(inputSchema);
                                var requiredFields = schemaRequiredFields(inputSchema);

                                assertThat(requiredFieldsByTool).containsKey(toolName);
                                var expectedRequiredFields = requiredFieldsByTool.get(toolName);
                                var expectedOptionalFields = optionalFieldsByTool.get(toolName);

                                assertThat(schemaProperties)
                                        .containsKeys(
                                                expectedRequiredFields.toArray(String[]::new));
                                if (!expectedOptionalFields.isEmpty()) {
                                    assertThat(schemaProperties)
                                            .containsKeys(
                                                    expectedOptionalFields.toArray(String[]::new));
                                }
                                assertThat(schemaProperties).doesNotContainKey("request");
                                assertThat(requiredFields)
                                        .containsExactlyInAnyOrderElementsOf(
                                                expectedRequiredFields);
                                if (!expectedOptionalFields.isEmpty()) {
                                    assertThat(requiredFields)
                                            .doesNotContainAnyElementsOf(expectedOptionalFields);
                                }
                            });
        }

        @Test
        void mcpEndpointListsToolsWhenEnabled() throws Exception {
            mcpClient()
                    .perform(
                            post("/mcp")
                                    .contentType(APPLICATION_JSON)
                                    .accept(APPLICATION_JSON, TEXT_EVENT_STREAM)
                                    .content(
                                            """
                                            {
                                              "jsonrpc": "2.0",
                                              "id": 1,
                                              "method": "tools/list",
                                              "params": {}
                                            }
                                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").doesNotExist())
                    .andExpect(
                            jsonPath("$.result.tools[*].name")
                                    .value(
                                            containsInAnyOrder(
                                                    DEFAULT_TOOL_NAMES.toArray(String[]::new))));
        }

        private MockMvc mcpClient() {
            return MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        }

        private List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecifications() {
            return syncToolSpecifications.stream().flatMap(List::stream).toList();
        }

        private List<String> toolNames() {
            return toolSpecifications().stream().map(spec -> spec.tool().name()).sorted().toList();
        }
    }

    @Nested
    @SpringBootTest(classes = {MemindServerApplication.class, NoopRuntimeTestConfiguration.class})
    class GovernanceEnabled {

        private static final Path DB_PATH =
                Path.of("target", "memind-mcp-governance-" + UUID.randomUUID() + ".db")
                        .toAbsolutePath();

        @Autowired private ApplicationContext applicationContext;
        @Autowired private WebApplicationContext webApplicationContext;

        @Autowired
        private ObjectProvider<List<McpStatelessServerFeatures.SyncToolSpecification>>
                syncToolSpecifications;

        @DynamicPropertySource
        static void registerProperties(DynamicPropertyRegistry registry) {
            prepareDatabasePath(DB_PATH);
            registry.add("spring.main.web-application-type", () -> "servlet");
            registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH);
            registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
            registry.add("memind.store.init-schema", () -> "true");
            registry.add("spring.ai.mcp.server.enabled", () -> "true");
            registry.add("memind.mcp.governance-enabled", () -> "true");
            registry.add(
                    "spring.autoconfigure.exclude",
                    () -> NoopRuntimeTestConfiguration.SPRING_AI_AUTOCONFIG_EXCLUDES);
        }

        @Test
        void contextRegistersGovernanceToolWhenEnabled() {
            assertThat(applicationContext.getBeanNamesForType(MemindMcpGovernanceToolService.class))
                    .hasSize(1);
            assertThat(toolNames()).containsExactlyElementsOf(GOVERNANCE_TOOL_NAMES);
        }

        @Test
        void mcpEndpointListsGovernanceToolWhenEnabled() throws Exception {
            MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .build()
                    .perform(
                            post("/mcp")
                                    .contentType(APPLICATION_JSON)
                                    .accept(APPLICATION_JSON, TEXT_EVENT_STREAM)
                                    .content(
                                            """
                                            {
                                              "jsonrpc": "2.0",
                                              "id": 1,
                                              "method": "tools/list",
                                              "params": {}
                                            }
                                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").doesNotExist())
                    .andExpect(
                            jsonPath("$.result.tools[*].name")
                                    .value(
                                            containsInAnyOrder(
                                                    GOVERNANCE_TOOL_NAMES.toArray(String[]::new))));
        }

        private List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecifications() {
            return syncToolSpecifications.stream().flatMap(List::stream).toList();
        }

        private List<String> toolNames() {
            return toolSpecifications().stream().map(spec -> spec.tool().name()).sorted().toList();
        }
    }

    @Nested
    @SpringBootTest(classes = {MemindServerApplication.class, NoopRuntimeTestConfiguration.class})
    class Disabled {

        private static final Path DB_PATH =
                Path.of("target", "memind-mcp-disabled-" + UUID.randomUUID() + ".db")
                        .toAbsolutePath();

        @Autowired private ApplicationContext applicationContext;
        @Autowired private WebApplicationContext webApplicationContext;

        @Autowired
        private ObjectProvider<List<McpStatelessServerFeatures.SyncToolSpecification>>
                syncToolSpecifications;

        @DynamicPropertySource
        static void registerProperties(DynamicPropertyRegistry registry) {
            prepareDatabasePath(DB_PATH);
            registry.add("spring.main.web-application-type", () -> "servlet");
            registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH);
            registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
            registry.add("memind.store.init-schema", () -> "true");
            registry.add("spring.ai.mcp.server.enabled", () -> "false");
            registry.add(
                    "spring.autoconfigure.exclude",
                    () -> NoopRuntimeTestConfiguration.SPRING_AI_AUTOCONFIG_EXCLUDES);
        }

        @Test
        void disabledConfigurationDoesNotPublishMcpServerOrToolSpecifications() {
            assertThat(applicationContext.getBeanNamesForType(MemindMcpToolService.class))
                    .hasSize(1);
            assertThat(applicationContext.getBeanProvider(McpStatelessSyncServer.class).stream())
                    .isEmpty();
            assertThat(syncToolSpecifications.stream()).isEmpty();
        }

        @Test
        void mcpEndpointIsUnavailableWhenDisabled() throws Exception {
            MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .build()
                    .perform(
                            post("/mcp")
                                    .contentType(APPLICATION_JSON)
                                    .accept(APPLICATION_JSON, TEXT_EVENT_STREAM)
                                    .content(
                                            """
                                            {
                                              "jsonrpc": "2.0",
                                              "id": 1,
                                              "method": "tools/list",
                                              "params": {}
                                            }
                                            """))
                    .andExpect(status().is4xxClientError());
        }
    }

    private static Map<String, List<String>> requiredFieldsByDefaultTool() {
        return Map.ofEntries(
                entry("memind_add_message", List.of("userId", "agentId", "role", "content")),
                entry("memind_commit", List.of("userId", "agentId")),
                entry("memind_compile_context", List.of("userId", "agentId", "query")),
                entry("memind_extract_rawdata", List.of("userId", "agentId", "type", "content")),
                entry("memind_extract_text", List.of("userId", "agentId", "text")),
                entry("memind_items_get", List.of("userId", "agentId", "ids")),
                entry("memind_items_search", List.of("userId", "agentId")),
                entry("memind_items_sources", List.of("userId", "agentId", "ids")),
                entry("memind_rawdata_get", List.of("userId", "agentId", "ids")),
                entry("memind_rawdata_search", List.of("userId", "agentId")),
                entry("memind_recent", List.of("userId", "agentId")),
                entry("memind_retrieve", List.of("userId", "agentId", "query")));
    }

    private static Map<String, List<String>> optionalFieldsByDefaultTool() {
        return Map.ofEntries(
                entry("memind_add_message", List.of("sourceClient")),
                entry("memind_commit", List.of("sourceClient")),
                entry(
                        "memind_compile_context",
                        List.of(
                                "strategy",
                                "maxItems",
                                "tokenBudget",
                                "includeSources",
                                "metadataFilter")),
                entry("memind_extract_rawdata", List.of("metadata", "sourceClient")),
                entry("memind_extract_text", List.of("sourceClient")),
                entry("memind_items_get", List.of()),
                entry(
                        "memind_items_search",
                        List.of(
                                "scope",
                                "categories",
                                "sourceClients",
                                "rawDataTypes",
                                "timeRange",
                                "metadataFilter",
                                "limit",
                                "cursor")),
                entry("memind_items_sources", List.of("includeSegment")),
                entry("memind_rawdata_get", List.of("includeSegment")),
                entry(
                        "memind_rawdata_search",
                        List.of(
                                "types",
                                "sourceClients",
                                "timeRange",
                                "metadataFilter",
                                "includeSegment",
                                "includeMetadata",
                                "limit",
                                "cursor")),
                entry("memind_recent", List.of("types", "limit", "cursor", "metadataFilter")),
                entry("memind_retrieve", List.of("strategy", "trace")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperties(Map<String, Object> inputSchema) {
        Object properties = inputSchema.get("properties");
        assertThat(properties).isInstanceOf(Map.class);
        return (Map<String, Object>) properties;
    }

    private static List<String> schemaRequiredFields(Map<String, Object> inputSchema) {
        Object required = inputSchema.get("required");
        if (required == null) {
            return List.of();
        }
        assertThat(required).isInstanceOf(List.class);
        return ((List<?>) required).stream().map(String.class::cast).toList();
    }

    private static void prepareDatabasePath(Path dbPath) {
        try {
            Files.createDirectories(dbPath.getParent());
            Files.deleteIfExists(dbPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare test database path", exception);
        }
    }
}
