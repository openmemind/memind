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
package com.openmemind.ai.memory.plugin.jdbc.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.resource.ResourceRef;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.GraphOperationsCapabilities;
import com.openmemind.ai.memory.core.store.graph.ItemGraphCommitOperations;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import com.openmemind.ai.memory.plugin.jdbc.JdbcMemoryAccess;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JsonCodec;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteBubbleTrackerStore;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteMemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteMemoryTextSearch;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@DisplayName("JDBC starter SQLite auto-configuration")
class JdbcPluginAutoConfigurationSqliteTest {

    private ApplicationContextRunner contextRunner() {
        return contextRunner(true);
    }

    private ApplicationContextRunner contextRunner(boolean initSchema) {
        try {
            Path dbPath = Files.createTempFile("memind-jdbc-starter-", ".db");
            return new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    DataSourceAutoConfiguration.class,
                                    JdbcPluginAutoConfiguration.class))
                    .withPropertyValues(
                            "spring.datasource.url=jdbc:sqlite:" + dbPath,
                            "spring.datasource.driver-class-name=org.sqlite.JDBC",
                            "spring.datasource.hikari.maximum-pool-size=1",
                            "spring.datasource.hikari.minimum-idle=1",
                            "memind.store.init-schema=" + initSchema);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName(
            "Create SQLite memory store and text search from datasource with buffers owned by"
                    + " store")
    void createsSqliteStoreAndTextSearchWithAggregateBuffers() {
        contextRunner()
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(JdbcMemoryAccess.class);
                            assertThat(context).hasSingleBean(HikariDataSource.class);
                            assertThat(context).hasSingleBean(MemoryStore.class);
                            assertThat(context).hasSingleBean(MemoryBuffer.class);
                            assertThat(context).hasSingleBean(MemoryTextSearch.class);
                            assertThat(context).hasSingleBean(GraphOperations.class);
                            assertThat(context).hasSingleBean(GraphOperationsCapabilities.class);
                            assertThat(context).hasSingleBean(ItemGraphCommitOperations.class);
                            assertThat(context).hasSingleBean(ThreadProjectionStore.class);
                            assertThat(context).hasSingleBean(ThreadEnrichmentInputStore.class);
                            assertThat(context.getBean(MemoryTextSearch.class))
                                    .isInstanceOf(SqliteMemoryTextSearch.class);
                            assertThat(context).doesNotHaveBean(InsightBuffer.class);
                            assertThat(context).doesNotHaveBean(PendingConversationBuffer.class);
                            assertThat(context).doesNotHaveBean(RecentConversationBuffer.class);

                            DataSource dataSource = context.getBean(DataSource.class);
                            MemoryStore memoryStore = context.getBean(MemoryStore.class);
                            MemoryBuffer memoryBuffer = context.getBean(MemoryBuffer.class);
                            assertThat(memoryStore.rawDataOperations())
                                    .isInstanceOf(SqliteMemoryStore.class);
                            assertThat(memoryStore.itemOperations())
                                    .isInstanceOf(SqliteMemoryStore.class);
                            assertThat(memoryStore.insightOperations())
                                    .isInstanceOf(SqliteMemoryStore.class);
                            assertThat(memoryStore.resourceOperations())
                                    .isInstanceOf(SqliteMemoryStore.class);
                            assertThat(context.getBean(GraphOperations.class))
                                    .isSameAs(memoryStore.graphOperations());
                            assertThat(context.getBean(GraphOperationsCapabilities.class))
                                    .isSameAs(memoryStore.graphOperationsCapabilities());
                            assertThat(context.getBean(ItemGraphCommitOperations.class))
                                    .isSameAs(memoryStore.itemGraphCommitOperations());
                            assertThat(context.getBean(ThreadProjectionStore.class))
                                    .isNotInstanceOf(ThreadEnrichmentInputStore.class);
                            assertThat(context.getBean(ThreadEnrichmentInputStore.class))
                                    .isNotInstanceOf(ThreadProjectionStore.class);
                            assertThat(tableExists(dataSource, "memory_item")).isTrue();
                            assertThat(tableExists(dataSource, "item_fts")).isTrue();
                            assertThat(tableExists(dataSource, "memory_insight_buffer")).isTrue();
                            assertThat(tableExists(dataSource, "memory_conversation_buffer"))
                                    .isTrue();
                            assertThat(tableExists(dataSource, "memory_resource")).isTrue();

                            executeUpdate(
                                    dataSource,
                                    """
                                    INSERT INTO memory_item (
                                        biz_id, user_id, agent_id, memory_id, content, scope,
                                        occurred_at, type, raw_data_type, deleted
                                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                                    1L,
                                    "user-1",
                                    "agent-1",
                                    "user-1:agent-1",
                                    "hello sqlite search",
                                    "USER",
                                    "2026-03-22T00:00:00Z",
                                    "FACT",
                                    "CONVERSATION",
                                    0);

                            List<TextSearchResult> results =
                                    context.getBean(MemoryTextSearch.class)
                                            .search(
                                                    DefaultMemoryId.of("user-1", "agent-1"),
                                                    "sqlite",
                                                    5,
                                                    MemoryTextSearch.SearchTarget.ITEM)
                                            .block();

                            assertThat(results).isNotNull().hasSize(1);
                            assertThat(results.getFirst().documentId()).isEqualTo("1");

                            DefaultMemoryId memoryId = DefaultMemoryId.of("user-1", "agent-1");
                            var insightBufferStore = memoryBuffer.insightBuffer();
                            insightBufferStore.append(memoryId, "profile", List.of(101L, 102L));
                            insightBufferStore.assignGroup(
                                    memoryId, "profile", List.of(101L), "food");

                            assertThat(insightBufferStore.countUnGrouped(memoryId, "profile"))
                                    .isEqualTo(1);
                            assertThat(
                                            insightBufferStore.getGroupUnbuilt(
                                                    memoryId, "profile", "food"))
                                    .extracting(entry -> entry.itemId())
                                    .containsExactly(101L);

                            PendingConversationBuffer conversationBufferStore =
                                    memoryBuffer.pendingConversationBuffer();
                            String sessionId = memoryId.toIdentifier();
                            Message userMessage =
                                    Message.user(
                                            "hello",
                                            java.time.Instant.parse("2026-03-22T00:00:00Z"));
                            Message assistantMessage =
                                    Message.assistant(
                                            "hi", java.time.Instant.parse("2026-03-22T00:00:01Z"));
                            Message followUp =
                                    Message.user(
                                            "next",
                                            java.time.Instant.parse("2026-03-22T00:00:02Z"));

                            conversationBufferStore.append(sessionId, userMessage);
                            conversationBufferStore.append(sessionId, assistantMessage);

                            assertThat(conversationBufferStore.load(sessionId))
                                    .extracting(Message::textContent)
                                    .containsExactly("hello", "hi");

                            conversationBufferStore.clear(sessionId);

                            assertThat(conversationBufferStore.load(sessionId)).isEmpty();

                            conversationBufferStore.append(sessionId, followUp);

                            assertThat(conversationBufferStore.load(sessionId))
                                    .extracting(Message::textContent)
                                    .containsExactly("next");
                            assertThat(
                                            memoryBuffer
                                                    .recentConversationBuffer()
                                                    .loadRecent(sessionId, 10))
                                    .extracting(Message::textContent)
                                    .containsExactly("hello", "hi", "next");
                        });
    }

    @Test
    @DisplayName("Wire optional ResourceStore into JDBC-backed MemoryStore")
    void wiresOptionalResourceStore() {
        contextRunner()
                .withUserConfiguration(ResourceStoreConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(MemoryStore.class).resourceStore())
                                    .isSameAs(context.getBean(ResourceStore.class));
                        });
    }

    @Test
    @DisplayName("Expose BubbleTrackerStore bean for SQLite JDBC starter")
    void exposesBubbleTrackerStoreBean() {
        contextRunner()
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(BubbleTrackerStore.class);
                            assertThat(context.getBean(BubbleTrackerStore.class))
                                    .isInstanceOf(SqliteBubbleTrackerStore.class);
                        });
    }

    @Test
    @DisplayName("Use the application ObjectMapper for JDBC JSON codec")
    void usesApplicationObjectMapperForJdbcJsonCodec() {
        contextRunner()
                .withUserConfiguration(RawContentObjectMapperConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();

                            SqliteMemoryStore store =
                                    (SqliteMemoryStore) context.getBean(MemoryStore.class);
                            JsonCodec jsonCodec = readField(store, "jsonHelper", JsonCodec.class);
                            RawContent restored =
                                    jsonCodec.fromJson(
                                            "{\"type\":\"test_raw\",\"text\":\"hello jdbc\"}",
                                            RawContent.class);

                            assertThat(restored).isInstanceOf(TestRawContent.class);
                            assertThat(restored.toContentString()).isEqualTo("hello jdbc");
                        });
    }

    @Test
    @DisplayName("Expose default taxonomy after first JDBC store bootstrap without runtime seeder")
    void exposesDefaultTaxonomyWithoutRuntimeSeeder() {
        contextRunner()
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean("defaultTaxonomySeeder");

                            MemoryStore memoryStore = context.getBean(MemoryStore.class);

                            assertThat(memoryStore.insightOperations().listInsightTypes())
                                    .extracting(type -> type.name())
                                    .containsExactlyInAnyOrderElementsOf(
                                            DefaultInsightTypes.all().stream()
                                                    .map(type -> type.name())
                                                    .toList());
                        });
    }

    @Test
    @DisplayName("Respect memind.store.init-schema=false")
    void respectsInitSchemaFlag() {
        contextRunner(false)
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .hasMessageContaining("memory_raw_data");
                        });
    }

    @Test
    @DisplayName("Back off fully when user provides store and text search beans")
    void backsOffFullyForUserProvidedStoreAndTextSearch() {
        contextRunner()
                .withUserConfiguration(CustomStoreAndTextSearchConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(MemoryStore.class);
                            assertThat(context.getBean(MemoryStore.class))
                                    .isSameAs(context.getBean("customMemoryStore"));
                            assertThat(context).hasSingleBean(MemoryTextSearch.class);
                            assertThat(context.getBean(MemoryTextSearch.class))
                                    .isSameAs(context.getBean("customMemoryTextSearch"));
                            assertThat(context).doesNotHaveBean(JdbcMemoryAccess.class);
                            assertThat(context).doesNotHaveBean(InsightBuffer.class);
                            assertThat(context).doesNotHaveBean(PendingConversationBuffer.class);
                            assertThat(context).doesNotHaveBean(RecentConversationBuffer.class);
                            assertThat(context).doesNotHaveBean(JdbcDialectDetector.class);
                        });
    }

    private boolean tableExists(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT name FROM sqlite_master WHERE type='table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void executeUpdate(DataSource dataSource, String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStoreAndTextSearchConfig {

        @Bean
        MemoryStore customMemoryStore() {
            return new InMemoryMemoryStore();
        }

        @Bean
        MemoryTextSearch customMemoryTextSearch() {
            return (memoryId, query, topK, target) -> Mono.just(List.of());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ResourceStoreConfig {

        @Bean
        ResourceStore resourceStore() {
            return new ResourceStore() {
                @Override
                public Mono<ResourceRef> store(
                        MemoryId memoryId,
                        String fileName,
                        byte[] data,
                        String mimeType,
                        Map<String, Object> metadata) {
                    return Mono.empty();
                }

                @Override
                public Mono<byte[]> retrieve(ResourceRef ref) {
                    return Mono.empty();
                }

                @Override
                public Mono<Void> delete(ResourceRef ref) {
                    return Mono.empty();
                }

                @Override
                public Mono<Boolean> exists(ResourceRef ref) {
                    return Mono.just(false);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RawContentObjectMapperConfig {

        @Bean
        ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper = RawContentJackson.registerCoreSubtypes(mapper);
            mapper =
                    RawContentJackson.registerPluginSubtypes(
                            mapper, List.of(() -> Map.of("test_raw", TestRawContent.class)));
            return mapper;
        }
    }

    private static <T> T readField(Object target, String name, Class<T> type) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class TestRawContent extends RawContent {

        private final String text;

        @JsonCreator
        private TestRawContent(@JsonProperty("text") String text) {
            this.text = text == null ? "" : text;
        }

        @Override
        public String contentType() {
            return "TEST_RAW";
        }

        @Override
        public String toContentString() {
            return text;
        }

        @Override
        public String getContentId() {
            return text;
        }
    }
}
