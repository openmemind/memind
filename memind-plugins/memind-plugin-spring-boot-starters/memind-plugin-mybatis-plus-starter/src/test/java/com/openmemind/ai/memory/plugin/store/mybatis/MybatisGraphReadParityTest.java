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

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.causal.CausalRelationCode;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticEvidenceSource;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.plugin.store.mybatis.handler.InstantTypeHandler;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.MemorySchemaAutoConfiguration;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
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

class MybatisGraphReadParityTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-04-19T00:00:00Z");

    @Test
    void mybatisAndInMemoryGraphReadsProduceEquivalentAdjacencyAndReverseMentionResults(
            @TempDir Path tempDir) {
        GraphFixture fixture = GraphFixture.sample();
        var inMemory = new InMemoryGraphOperations();
        fixture.seed(inMemory);

        newContextRunner(tempDir.resolve("graph-parity.db"))
                .run(
                        context -> {
                            GraphOperations mybatis =
                                    context.getBean(MemoryStore.class).graphOperations();
                            fixture.seed(mybatis);

                            assertThat(
                                            mybatis.listAdjacentItemLinks(
                                                    MEMORY_ID,
                                                    fixture.seedIds(),
                                                    fixture.linkTypes()))
                                    .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
                                            "createdAt")
                                    .isEqualTo(
                                            inMemory.listAdjacentItemLinks(
                                                    MEMORY_ID,
                                                    fixture.seedIds(),
                                                    fixture.linkTypes()));

                            assertThat(
                                            mybatis.listItemEntityMentionsByEntityKeys(
                                                    MEMORY_ID, fixture.entityKeys(), 4))
                                    .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
                                            "createdAt")
                                    .isEqualTo(
                                            inMemory.listItemEntityMentionsByEntityKeys(
                                                    MEMORY_ID, fixture.entityKeys(), 4));
                        });
    }

    @Test
    void mybatisAndInMemoryGraphReadsProduceEquivalentEntityKeyLookups(@TempDir Path tempDir) {
        GraphFixture fixture = GraphFixture.sample();
        var inMemory = new InMemoryGraphOperations();
        fixture.seed(inMemory);

        newContextRunner(tempDir.resolve("graph-entity-lookup-parity.db"))
                .run(
                        context -> {
                            GraphOperations mybatis =
                                    context.getBean(MemoryStore.class).graphOperations();
                            fixture.seed(mybatis);

                            assertThat(
                                            mybatis.listEntitiesByEntityKeys(
                                                    MEMORY_ID,
                                                    List.of(
                                                            "person:sam_altman",
                                                            "organization:openai",
                                                            "person:sam_altman",
                                                            "missing:key")))
                                    .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
                                            "createdAt", "updatedAt")
                                    .isEqualTo(
                                            inMemory.listEntitiesByEntityKeys(
                                                    MEMORY_ID,
                                                    List.of(
                                                            "person:sam_altman",
                                                            "organization:openai",
                                                            "person:sam_altman",
                                                            "missing:key")));
                        });
    }

    @Test
    void mybatisAndInMemoryGraphReadsPreserveEquivalentStage2MetadataSemantics(
            @TempDir Path tempDir) {
        var entity =
                new GraphEntity(
                        "organization:openai",
                        MEMORY_ID.toIdentifier(),
                        "OpenAI",
                        GraphEntityType.ORGANIZATION,
                        Map.of(
                                "topAliases",
                                List.of("开放人工智能"),
                                "aliasEvidenceCount",
                                1,
                                "aliasClasses",
                                List.of("explicit_parenthetical")),
                        Instant.parse("2026-04-16T00:00:00Z"),
                        Instant.parse("2026-04-16T00:00:00Z"));
        var mention =
                new ItemEntityMention(
                        MEMORY_ID.toIdentifier(),
                        101L,
                        "organization:openai",
                        0.95f,
                        Map.of(
                                "resolutionMode",
                                "conservative",
                                "resolutionSource",
                                "explicit_alias_evidence_hit",
                                "resolvedViaAliasClass",
                                "explicit_parenthetical"),
                        Instant.parse("2026-04-16T00:00:00Z"));
        var inMemory = new InMemoryGraphOperations();
        inMemory.upsertEntities(MEMORY_ID, List.of(entity));
        inMemory.upsertItemEntityMentions(MEMORY_ID, List.of(mention));

        newContextRunner(tempDir.resolve("graph-stage2-parity.db"))
                .run(
                        context -> {
                            GraphOperations mybatis =
                                    context.getBean(MemoryStore.class).graphOperations();
                            mybatis.upsertEntities(MEMORY_ID, List.of(entity));
                            mybatis.upsertItemEntityMentions(MEMORY_ID, List.of(mention));

                            var inMemoryEntity = inMemory.listEntities(MEMORY_ID).getFirst();
                            var mybatisEntity = mybatis.listEntities(MEMORY_ID).getFirst();
                            assertThat(
                                            ((Number)
                                                            mybatisEntity
                                                                    .metadata()
                                                                    .get("aliasEvidenceCount"))
                                                    .intValue())
                                    .isEqualTo(
                                            ((Number)
                                                            inMemoryEntity
                                                                    .metadata()
                                                                    .get("aliasEvidenceCount"))
                                                    .intValue());
                            assertThat(mybatisEntity.metadata().get("aliasClasses"))
                                    .isEqualTo(inMemoryEntity.metadata().get("aliasClasses"));
                            assertThat(mybatisEntity.metadata().get("topAliases"))
                                    .isEqualTo(inMemoryEntity.metadata().get("topAliases"));

                            var inMemoryMention =
                                    inMemory.listItemEntityMentions(MEMORY_ID).getFirst();
                            var mybatisMention =
                                    mybatis.listItemEntityMentions(MEMORY_ID).getFirst();
                            assertThat(mybatisMention.metadata().get("resolutionMode"))
                                    .isEqualTo(inMemoryMention.metadata().get("resolutionMode"));
                            assertThat(mybatisMention.metadata().get("resolutionSource"))
                                    .isEqualTo(inMemoryMention.metadata().get("resolutionSource"));
                            assertThat(mybatisMention.metadata().get("resolvedViaAliasClass"))
                                    .isEqualTo(
                                            inMemoryMention
                                                    .metadata()
                                                    .get("resolvedViaAliasClass"));
                        });
    }

    @Test
    void mybatisAndInMemoryHistoricalAliasLookupProduceEquivalentResults(@TempDir Path tempDir) {
        var inMemory = new InMemoryGraphOperations();
        inMemory.upsertEntityAliases(
                MEMORY_ID,
                List.of(
                        alias("organization:google", GraphEntityType.ORGANIZATION, "谷歌", 2),
                        alias("organization:google_hk", GraphEntityType.ORGANIZATION, "谷歌", 1)));

        newContextRunner(tempDir.resolve("graph-historical-alias-parity.db"))
                .run(
                        context -> {
                            GraphOperations mybatis =
                                    context.getBean(MemoryStore.class).graphOperations();
                            mybatis.upsertEntityAliases(
                                    MEMORY_ID,
                                    List.of(
                                            alias(
                                                    "organization:google",
                                                    GraphEntityType.ORGANIZATION,
                                                    "谷歌",
                                                    2),
                                            alias(
                                                    "organization:google_hk",
                                                    GraphEntityType.ORGANIZATION,
                                                    "谷歌",
                                                    1)));

                            assertThat(
                                            mybatis.listEntityAliasesByNormalizedAlias(
                                                    MEMORY_ID, GraphEntityType.ORGANIZATION, "谷歌"))
                                    .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
                                            "createdAt", "updatedAt")
                                    .isEqualTo(
                                            inMemory.listEntityAliasesByNormalizedAlias(
                                                    MEMORY_ID, GraphEntityType.ORGANIZATION, "谷歌"));
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

    private static GraphEntityAlias alias(
            String entityKey,
            GraphEntityType entityType,
            String normalizedAlias,
            int evidenceCount) {
        return new GraphEntityAlias(
                MEMORY_ID.toIdentifier(),
                entityKey,
                entityType,
                normalizedAlias,
                evidenceCount,
                Map.of("source", "item_extraction"),
                NOW,
                NOW);
    }

    private record GraphFixture(
            List<GraphEntity> entities,
            List<ItemEntityMention> mentions,
            List<ItemLink> links,
            List<Long> seedIds,
            List<String> entityKeys,
            List<ItemLinkType> linkTypes) {

        private static final Instant NOW = Instant.parse("2026-04-16T00:00:00Z");

        private static GraphFixture sample() {
            return new GraphFixture(
                    List.of(
                            new GraphEntity(
                                    "organization:openai",
                                    MEMORY_ID.toIdentifier(),
                                    "OpenAI",
                                    GraphEntityType.ORGANIZATION,
                                    Map.of(),
                                    NOW,
                                    NOW),
                            new GraphEntity(
                                    "person:sam_altman",
                                    MEMORY_ID.toIdentifier(),
                                    "Sam Altman",
                                    GraphEntityType.PERSON,
                                    Map.of(),
                                    NOW,
                                    NOW)),
                    List.of(
                            new ItemEntityMention(
                                    MEMORY_ID.toIdentifier(),
                                    101L,
                                    "organization:openai",
                                    0.95f,
                                    Map.of(),
                                    NOW),
                            new ItemEntityMention(
                                    MEMORY_ID.toIdentifier(),
                                    102L,
                                    "organization:openai",
                                    0.95f,
                                    Map.of(),
                                    NOW),
                            new ItemEntityMention(
                                    MEMORY_ID.toIdentifier(),
                                    103L,
                                    "person:sam_altman",
                                    0.95f,
                                    Map.of(),
                                    NOW),
                            new ItemEntityMention(
                                    MEMORY_ID.toIdentifier(),
                                    104L,
                                    "organization:openai",
                                    0.95f,
                                    Map.of(),
                                    NOW)),
                    List.of(
                            new ItemLink(
                                    MEMORY_ID.toIdentifier(),
                                    101L,
                                    102L,
                                    ItemLinkType.CAUSAL,
                                    CausalRelationCode.CAUSED_BY.code(),
                                    null,
                                    0.92d,
                                    Map.of(),
                                    NOW),
                            new ItemLink(
                                    MEMORY_ID.toIdentifier(),
                                    103L,
                                    101L,
                                    ItemLinkType.CAUSAL,
                                    CausalRelationCode.CAUSED_BY.code(),
                                    null,
                                    0.88d,
                                    Map.of(),
                                    NOW),
                            new ItemLink(
                                    MEMORY_ID.toIdentifier(),
                                    101L,
                                    104L,
                                    ItemLinkType.SEMANTIC,
                                    null,
                                    SemanticEvidenceSource.VECTOR_SEARCH.code(),
                                    0.81d,
                                    Map.of(),
                                    NOW)),
                    List.of(101L),
                    List.of("organization:openai", "person:sam_altman"),
                    List.of(ItemLinkType.CAUSAL, ItemLinkType.SEMANTIC));
        }

        private void seed(GraphOperations ops) {
            ops.upsertEntities(MEMORY_ID, entities);
            ops.upsertItemEntityMentions(MEMORY_ID, mentions);
            ops.upsertItemLinks(MEMORY_ID, links);
        }
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
