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
package com.openmemind.ai.memory.plugin.store.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.graph.NoOpGraphOperations;
import com.openmemind.ai.memory.plugin.store.mybatis.handler.InstantTypeHandler;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.MemorySchemaAutoConfiguration;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.sqlite.SQLiteDataSource;

@DisplayName("MyBatis graph operations")
class MybatisGraphOperationsTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    @DisplayName("memory store exposes real graph operations once graph backend is wired")
    void memoryStoreExposesRealGraphOperations(@TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-store.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);

                            assertThat(store.graphOperations())
                                    .isNotSameAs(NoOpGraphOperations.INSTANCE);
                        });
    }

    @Test
    @DisplayName("repeated graph writes stay idempotent across MyBatis persistence")
    void repeatedGraphWritesStayIdempotent(@TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-idempotent.db"))
                .run(
                        context -> {
                            GraphOperations ops =
                                    context.getBean(MemoryStore.class).graphOperations();

                            seedGraph(ops);
                            seedGraph(ops);

                            assertThat(ops.listEntities(MEMORY_ID)).hasSize(2);
                            assertThat(ops.listItemEntityMentions(MEMORY_ID)).hasSize(2);
                            assertThat(ops.listItemLinks(MEMORY_ID)).hasSize(3);
                        });
    }

    @Test
    @DisplayName("rebuilding cooccurrences twice keeps a single deterministic aggregate")
    void rebuildEntityCooccurrencesRemainsDeterministic(@TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-cooccurrence.db"))
                .run(
                        context -> {
                            GraphOperations ops =
                                    context.getBean(MemoryStore.class).graphOperations();
                            seedMentions(ops, "organization:openai", 101L, 102L);
                            seedMentions(ops, "person:sam_altman", 101L, 102L);

                            ops.rebuildEntityCooccurrences(
                                    MEMORY_ID, List.of("organization:openai", "person:sam_altman"));
                            ops.rebuildEntityCooccurrences(
                                    MEMORY_ID, List.of("organization:openai", "person:sam_altman"));

                            assertThat(ops.listEntityCooccurrences(MEMORY_ID))
                                    .singleElement()
                                    .extracting(EntityCooccurrence::cooccurrenceCount)
                                    .isEqualTo(2);
                        });
    }

    @Test
    @DisplayName(
            "seed-side mention reads honor requested item ids without falling back to full-memory"
                    + " scans")
    void seedSideMentionReadsHonorRequestedItemIds(@TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-seed-mentions.db"))
                .run(
                        context -> {
                            GraphOperations ops =
                                    context.getBean(MemoryStore.class).graphOperations();
                            seedMentions(ops, "organization:openai", 101L, 102L, 103L);
                            seedMentions(ops, "person:sam_altman", 102L);

                            assertThat(ops.listItemEntityMentions(MEMORY_ID, List.of(102L, 103L)))
                                    .extracting(
                                            ItemEntityMention::itemId, ItemEntityMention::entityKey)
                                    .containsExactly(
                                            tuple(102L, "organization:openai"),
                                            tuple(102L, "person:sam_altman"),
                                            tuple(103L, "organization:openai"));
                        });
    }

    @Test
    @DisplayName("local-subgraph link reads honor both-endpoint semantics and link type")
    void localSubgraphLinkReadsHonorBothEndpointsAndType(@TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-local-subgraph.db"))
                .run(
                        context -> {
                            GraphOperations ops =
                                    context.getBean(MemoryStore.class).graphOperations();
                            seedGraph(ops);

                            assertThat(
                                            ops.listItemLinks(
                                                    MEMORY_ID,
                                                    List.of(101L, 102L, 103L),
                                                    List.of(ItemLinkType.CAUSAL)))
                                    .extracting(ItemLink::sourceItemId, ItemLink::targetItemId)
                                    .containsExactly(tuple(101L, 102L), tuple(103L, 101L));
                        });
    }

    @Test
    @DisplayName("adjacent link reads honor either endpoint and link type")
    void adjacentLinkReadsHonorEitherEndpointAndType(@TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-adjacency.db"))
                .run(
                        context -> {
                            GraphOperations ops =
                                    context.getBean(MemoryStore.class).graphOperations();
                            seedGraph(ops);

                            assertThat(
                                            ops.listAdjacentItemLinks(
                                                    MEMORY_ID,
                                                    List.of(101L),
                                                    List.of(ItemLinkType.CAUSAL)))
                                    .extracting(ItemLink::sourceItemId, ItemLink::targetItemId)
                                    .containsExactly(tuple(101L, 102L), tuple(103L, 101L));
                        });
    }

    @Test
    @DisplayName("reverse mention reads enforce per-entity L+1 without full memory scans")
    void reverseMentionReadsEnforcePerEntityLimitPlusOne(@TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-reverse-mentions.db"))
                .run(
                        context -> {
                            GraphOperations ops =
                                    context.getBean(MemoryStore.class).graphOperations();
                            seedMentions(ops, "organization:openai", 101L, 102L, 103L, 104L);

                            assertThat(
                                            ops.listItemEntityMentionsByEntityKeys(
                                                    MEMORY_ID, List.of("organization:openai"), 3))
                                    .hasSize(3);
                        });
    }

    @Test
    @DisplayName(
            "graph write and read round trip supports phase two and phase three paths together")
    void graphWriteAndReadRoundTripSupportsPhaseTwoAndPhaseThreePathsTogether(
            @TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-roundtrip.db"))
                .run(
                        context -> {
                            GraphOperations ops =
                                    context.getBean(MemoryStore.class).graphOperations();
                            seedGraph(ops);

                            assertThat(
                                            ops.listItemLinks(
                                                    MEMORY_ID,
                                                    List.of(101L, 102L, 103L),
                                                    List.of(ItemLinkType.CAUSAL)))
                                    .hasSize(2);
                            assertThat(
                                            ops.listAdjacentItemLinks(
                                                    MEMORY_ID,
                                                    List.of(101L),
                                                    List.of(ItemLinkType.CAUSAL)))
                                    .hasSize(2);
                        });
    }

    private ApplicationContextRunner newContextRunner(Path dbPath) {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                MemoryMybatisPlusAutoConfiguration.class,
                                MemorySchemaAutoConfiguration.class,
                                MybatisPlusAutoConfiguration.class))
                .withUserConfiguration(TestInfrastructureConfig.class)
                .withPropertyValues(
                        "test.sqlite.path=" + dbPath,
                        "memind.store.init-schema=true",
                        "spring.main.web-application-type=none");
    }

    private static void seedGraph(GraphOperations ops) {
        ops.upsertEntities(
                MEMORY_ID,
                List.of(
                        entity("organization:openai", "OpenAI", GraphEntityType.ORGANIZATION),
                        entity("person:sam_altman", "Sam Altman", GraphEntityType.PERSON)));
        ops.upsertItemEntityMentions(
                MEMORY_ID,
                List.of(mention(101L, "organization:openai"), mention(102L, "person:sam_altman")));
        ops.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(101L, 102L, ItemLinkType.CAUSAL),
                        link(103L, 101L, ItemLinkType.CAUSAL),
                        link(101L, 104L, ItemLinkType.SEMANTIC)));
    }

    private static void seedMentions(GraphOperations ops, String entityKey, Long... itemIds) {
        ops.upsertItemEntityMentions(
                MEMORY_ID,
                java.util.Arrays.stream(itemIds)
                        .map(itemId -> mention(itemId, entityKey))
                        .toList());
    }

    private static GraphEntity entity(
            String entityKey, String displayName, GraphEntityType entityType) {
        return new GraphEntity(
                entityKey, MEMORY_ID.toIdentifier(), displayName, entityType, Map.of(), NOW, NOW);
    }

    private static ItemEntityMention mention(long itemId, String entityKey) {
        return new ItemEntityMention(
                MEMORY_ID.toIdentifier(), itemId, entityKey, 1.0f, Map.of(), NOW);
    }

    private static ItemLink link(long sourceItemId, long targetItemId, ItemLinkType linkType) {
        return new ItemLink(
                MEMORY_ID.toIdentifier(),
                sourceItemId,
                targetItemId,
                linkType,
                1.0d,
                Map.of(),
                NOW);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SqliteTestSupportConfig.class)
    static class TestInfrastructureConfig {}

    @Configuration(proxyBeanMethods = false)
    static class SqliteTestSupportConfig {

        @Bean
        DataSource dataSource(@Value("${test.sqlite.path}") String dbPath) {
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + dbPath);
            return dataSource;
        }

        @Bean
        ConfigurationCustomizer instantTypeHandlerCustomizer() {
            return configuration ->
                    configuration
                            .getTypeHandlerRegistry()
                            .register(Instant.class, InstantTypeHandler.class);
        }

        @Bean
        InitializingBean ddlRunnerInitializer(DdlApplicationRunner ddlApplicationRunner) {
            return () -> ddlApplicationRunner.run(new DefaultApplicationArguments(new String[0]));
        }
    }
}
