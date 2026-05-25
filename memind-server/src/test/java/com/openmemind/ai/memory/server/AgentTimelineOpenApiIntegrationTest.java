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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.core.builder.ExtractionCommonOptions;
import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.InsightExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.PromptBudgetOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeFactory;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import com.openmemind.ai.memory.server.service.config.MemoryOptionService;
import com.openmemind.ai.memory.server.support.NoopRuntimeTestConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        classes = {
            MemindServerApplication.class,
            AgentTimelineOpenApiIntegrationTest.TestRuntimeConfiguration.class
        })
class AgentTimelineOpenApiIntegrationTest {

    private static final Path DB_PATH =
            Path.of("target", "memind-agent-timeline-openapi-" + UUID.randomUUID() + ".db")
                    .toAbsolutePath();

    private final ObjectMapper objectMapper = JsonUtils.mapper();

    @Autowired private WebApplicationContext webApplicationContext;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private MemoryRuntimeFactory memoryRuntimeFactory;

    @Autowired private MemoryRuntimeManager runtimeManager;

    @Autowired private MemoryOptionService memoryOptionService;

    @Autowired private TestMemoryVector memoryVector;

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
        clearDatabase();
        memoryVector.clear();

        MemoryRuntimeFactory.CreationResult created =
                memoryRuntimeFactory.create(testMemoryOptions());
        runtimeManager.swap(created.memory(), created.effectiveOptions(), 1L);
        memoryOptionService.getCurrent();
    }

    @AfterAll
    static void cleanUpDatabase() throws IOException {
        Files.deleteIfExists(DB_PATH);
    }

    @Test
    void syncExtractPersistsAgentEpisodeRawDataAndAgentItems() throws Exception {
        mockMvc.perform(
                        post("/open/v1/memory/sync/extract")
                                .contentType(APPLICATION_JSON)
                                .content(paymentTimelineRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.rawDataIds.length()").value(1))
                .andExpect(jsonPath("$.data.itemIds.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/admin/v1/items").queryParam("userId", "u").queryParam("agentId", "a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.page.totalItems", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items[*].category", hasItem("tool")))
                .andExpect(jsonPath("$.data.items[*].category", hasItem("resolution")));

        mockMvc.perform(
                        get("/admin/v1/items")
                                .queryParam("userId", "u")
                                .queryParam("agentId", "a")
                                .queryParam("category", "tool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.page.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].category").value("tool"));

        mockMvc.perform(
                        get("/admin/v1/raw-data")
                                .queryParam("userId", "u")
                                .queryParam("agentId", "a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.page.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].metadata.segmentType").value("agent_episode"))
                .andExpect(
                        jsonPath("$.data.items[0].segment.metadata.segmentType")
                                .value("agent_episode"));
    }

    @Test
    void duplicateTimelineSubmissionDoesNotIncreaseDurableItemCount() throws Exception {
        extractPaymentTimeline();
        int itemCountAfterFirstSubmit = itemCount();

        extractPaymentTimeline();

        assertThat(itemCount()).isEqualTo(itemCountAfterFirstSubmit);
    }

    @Test
    void retrieveReturnsAgentToolCategoryAndCommandsMetadata() throws Exception {
        extractPaymentTimeline();

        MvcResult result =
                mockMvc.perform(
                                post("/open/v1/memory/retrieve")
                                        .contentType(APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "userId": "u",
                                                  "agentId": "a",
                                                  "query": "How should payment tests be validated?",
                                                  "strategy": "SIMPLE"
                                                }
                                                """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").doesNotExist())
                        .andExpect(jsonPath("$.data.status").value("success"))
                        .andExpect(jsonPath("$.data.items.length()", greaterThanOrEqualTo(1)))
                        .andReturn();

        JsonNode items =
                objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .path("data")
                        .path("items");
        assertThat(items).anySatisfy(AgentTimelineOpenApiIntegrationTest::assertToolCommandItem);
    }

    private void extractPaymentTimeline() throws Exception {
        mockMvc.perform(
                        post("/open/v1/memory/sync/extract")
                                .contentType(APPLICATION_JSON)
                                .content(paymentTimelineRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    private static void assertToolCommandItem(JsonNode item) {
        assertThat(item.path("category").asText()).isEqualTo("tool");
        assertThat(item.path("metadata").path("commands"))
                .anySatisfy(command -> assertThat(command.asText()).isEqualTo("npm test payment"));
    }

    private int itemCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_item WHERE memory_id = ? AND deleted = 0",
                Integer.class,
                "u:a");
    }

    private void clearDatabase() {
        jdbcTemplate.update("DELETE FROM memory_graph_alias_batch_receipt");
        jdbcTemplate.update("DELETE FROM memory_entity_cooccurrence");
        jdbcTemplate.update("DELETE FROM memory_item_link");
        jdbcTemplate.update("DELETE FROM memory_item_entity_mention");
        jdbcTemplate.update("DELETE FROM memory_graph_entity_alias");
        jdbcTemplate.update("DELETE FROM memory_graph_entity");
        jdbcTemplate.update("DELETE FROM memory_item_graph_batch");
        jdbcTemplate.update("DELETE FROM memory_insight_buffer");
        jdbcTemplate.update("DELETE FROM memory_conversation_buffer");
        jdbcTemplate.update("DELETE FROM thread_intake_outbox");
        jdbcTemplate.update("DELETE FROM memory_thread_event");
        jdbcTemplate.update("DELETE FROM memory_thread_membership");
        jdbcTemplate.update("DELETE FROM memory_thread_runtime");
        jdbcTemplate.update("DELETE FROM memory_thread");
        jdbcTemplate.update("DELETE FROM memory_insight");
        jdbcTemplate.update("DELETE FROM memory_item");
        jdbcTemplate.update("DELETE FROM memory_raw_data");
        jdbcTemplate.update("DELETE FROM insight_fts");
        jdbcTemplate.update("DELETE FROM item_fts");
        jdbcTemplate.update("DELETE FROM raw_data_fts");
        jdbcTemplate.update("DELETE FROM memind_server_runtime_config");
    }

    private static MemoryBuildOptions testMemoryOptions() {
        return MemoryBuildOptions.builder()
                .extraction(
                        new ExtractionOptions(
                                ExtractionCommonOptions.defaults(),
                                RawDataExtractionOptions.defaults(),
                                new ItemExtractionOptions(
                                        false,
                                        PromptBudgetOptions.defaults(),
                                        ItemGraphOptions.defaults().withEnabled(false)),
                                new InsightExtractionOptions(
                                        false, new InsightBuildConfig(100, 100, 100, 100))))
                .build();
    }

    private static String paymentTimelineRequest() {
        return """
        {
          "userId": "u",
          "agentId": "a",
          "sourceClient": "claude-code",
          "rawContent": {
            "type": "agent_timeline",
            "sourceClient": "claude-code",
            "sessionId": "s",
            "agentTurnId": "s-agent-turn-1-5",
            "timelineId": "t",
            "project": {
              "name": "payments-api",
              "rootPath": "/Users/alice/work/payments-api"
            },
            "events": [
              {
                "eventId": "e1",
                "seq": 1,
                "kind": "user_prompt",
                "text": "Fix payment tests",
                "occurredAt": "2026-05-24T10:00:00Z"
              },
              {
                "eventId": "e2",
                "seq": 2,
                "kind": "command",
                "toolName": "Bash",
                "command": "npm test payment",
                "status": "failed",
                "output": "rounding mismatch",
                "metadata": {"failureSignal": "rounding mismatch"},
                "occurredAt": "2026-05-24T10:01:00Z"
              },
              {
                "eventId": "e3",
                "seq": 3,
                "kind": "file_edit",
                "path": "src/payment/calc.ts",
                "operation": "modify",
                "occurredAt": "2026-05-24T10:02:00Z"
              },
              {
                "eventId": "e4",
                "seq": 4,
                "kind": "command",
                "toolName": "Bash",
                "command": "npm test payment",
                "status": "success",
                "occurredAt": "2026-05-24T10:03:00Z"
              },
              {
                "eventId": "e5",
                "seq": 5,
                "kind": "stop",
                "occurredAt": "2026-05-24T10:04:00Z"
              }
            ]
          }
        }
        """;
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TestRuntimeConfiguration {

        @Bean
        StructuredChatClient structuredChatClient() {
            return new NoopStructuredChatClient();
        }

        @Bean
        @Primary
        TestMemoryVector memoryVector() {
            return new TestMemoryVector();
        }
    }

    private static final class NoopStructuredChatClient implements StructuredChatClient {

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.empty();
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            return Mono.empty();
        }
    }

    static final class TestMemoryVector implements MemoryVector {

        private final AtomicInteger sequence = new AtomicInteger();
        private final Map<String, StoredVector> vectors = new ConcurrentHashMap<>();

        void clear() {
            vectors.clear();
            sequence.set(0);
        }

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            String vectorId = "test-vector-" + sequence.incrementAndGet();
            vectors.put(vectorId, new StoredVector(memoryId.toIdentifier(), vectorId, text));
            return Mono.just(vectorId);
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            return Flux.fromIterable(texts)
                    .concatMap(text -> store(memoryId, text, Map.of()))
                    .collectList();
        }

        @Override
        public Mono<Void> delete(MemoryId memoryId, String vectorId) {
            vectors.remove(vectorId);
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteBatch(MemoryId memoryId, List<String> vectorIds) {
            vectorIds.forEach(vectors::remove);
            return Mono.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK) {
            return search(memoryId, query, topK, Map.of());
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            String memoryKey = memoryId.toIdentifier();
            return Flux.fromIterable(
                    vectors.values().stream()
                            .filter(vector -> vector.memoryId().equals(memoryKey))
                            .map(vector -> toSearchResult(vector, query))
                            .filter(result -> result.score() > 0.0f)
                            .sorted(Comparator.comparing(VectorSearchResult::score).reversed())
                            .limit(topK)
                            .toList());
        }

        @Override
        public Mono<List<Float>> embed(String text) {
            return Mono.just(embedding(text));
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.just(texts.stream().map(TestMemoryVector::embedding).toList());
        }

        private static VectorSearchResult toSearchResult(StoredVector vector, String query) {
            return new VectorSearchResult(
                    vector.vectorId(), vector.text(), lexicalScore(query, vector.text()), Map.of());
        }

        private static float lexicalScore(String query, String text) {
            List<String> queryTokens = tokens(query);
            List<String> textTokens = tokens(text);
            if (queryTokens.isEmpty() || textTokens.isEmpty()) {
                return 0.0f;
            }
            long matches = queryTokens.stream().filter(textTokens::contains).distinct().count();
            if (matches == 0 && text.toLowerCase(Locale.ROOT).contains("npm test payment")) {
                return 0.65f;
            }
            return matches == 0 ? 0.0f : Math.min(0.99f, 0.55f + (matches * 0.1f));
        }

        private static List<String> tokens(String value) {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (token.length() >= 3) {
                    result.add(token);
                }
            }
            return result;
        }

        private static List<Float> embedding(String text) {
            int hash = text == null ? 0 : text.hashCode();
            return IntStream.range(0, 8)
                    .mapToObj(index -> ((hash >> (index * 3)) & 0x0F) / 15.0f)
                    .toList();
        }

        private record StoredVector(String memoryId, String vectorId, String text) {}
    }
}
