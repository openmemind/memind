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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.MemoryResource;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.item.TemporalCandidateRequest;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.plugin.store.mybatis.handler.InstantTypeHandler;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryRawDataMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.MemorySchemaAutoConfiguration;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.sqlite.SQLiteDataSource;

@DisplayName("MybatisPlusMemoryStore batch operations")
class MybatisPlusMemoryStoreBatchOperationsTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant BASE_TIME = Instant.parse("2026-03-20T00:00:00Z");

    @TempDir Path tempDir;

    @Test
    @DisplayName("upsertRawData prefetches existing rows once before upsert")
    void upsertRawDataPrefetchesExistingRowsOnce() {
        newContextRunner(tempDir.resolve("save-raw-data.db"))
                .run(
                        context -> {
                            RawDataOperations rawDataOps =
                                    context.getBean(MemoryStore.class).rawDataOperations();
                            MapperMethodCounter counter =
                                    context.getBean(MapperMethodCounter.class);

                            rawDataOps.upsertRawData(
                                    MEMORY_ID,
                                    List.of(
                                            rawData(
                                                    "raw-1",
                                                    "old caption",
                                                    Map.of("source", "seed"))));
                            counter.reset();

                            rawDataOps.upsertRawData(
                                    MEMORY_ID,
                                    List.of(
                                            rawData(
                                                    "raw-1",
                                                    "new caption",
                                                    Map.of("source", "updated")),
                                            rawData(
                                                    "raw-2",
                                                    "second caption",
                                                    Map.of("source", "inserted"))));

                            assertThat(counter.count(MemoryRawDataMapper.class, "selectOne"))
                                    .isZero();
                            assertThat(counter.count(MemoryRawDataMapper.class, "selectList"))
                                    .isEqualTo(1);
                            assertThat(rawDataOps.listRawData(MEMORY_ID)).hasSize(2);
                            assertThat(rawDataOps.getRawData(MEMORY_ID, "raw-1"))
                                    .get()
                                    .extracting(MemoryRawData::caption)
                                    .isEqualTo("new caption");
                        });
    }

    @Test
    @DisplayName("deleteItems removes only the requested rows in batch")
    void deleteItemsRemovesOnlyRequestedRowsInBatch() {
        newContextRunner(tempDir.resolve("delete-items.db"))
                .run(
                        context -> {
                            ItemOperations itemOps =
                                    context.getBean(MemoryStore.class).itemOperations();

                            itemOps.insertItems(
                                    MEMORY_ID,
                                    List.of(
                                            memoryItem(1L, "first content"),
                                            memoryItem(2L, "second content"),
                                            memoryItem(3L, "third content")));

                            itemOps.deleteItems(MEMORY_ID, List.of(2L, 3L));

                            assertThat(itemOps.listItems(MEMORY_ID))
                                    .extracting(MemoryItem::id)
                                    .containsExactly(1L);
                        });
    }

    @Test
    @DisplayName("updateRawDataVectorIds prefetches existing rows once and merges metadata")
    void updateRawDataVectorIdsPrefetchesExistingRowsOnce() {
        newContextRunner(tempDir.resolve("update-raw-vectors.db"))
                .run(
                        context -> {
                            RawDataOperations rawDataOps =
                                    context.getBean(MemoryStore.class).rawDataOperations();
                            MapperMethodCounter counter =
                                    context.getBean(MapperMethodCounter.class);

                            rawDataOps.upsertRawData(
                                    MEMORY_ID,
                                    List.of(
                                            rawData(
                                                    "raw-1",
                                                    "caption",
                                                    Map.of("existing", "value"))));
                            counter.reset();

                            rawDataOps.updateRawDataVectorIds(
                                    MEMORY_ID,
                                    Map.of("raw-1", "vec-1", "raw-2", "vec-2"),
                                    Map.of("patched", "yes"));

                            assertThat(counter.count(MemoryRawDataMapper.class, "selectOne"))
                                    .isZero();
                            assertThat(counter.count(MemoryRawDataMapper.class, "selectList"))
                                    .isEqualTo(1);
                            assertThat(rawDataOps.getRawData(MEMORY_ID, "raw-1"))
                                    .get()
                                    .satisfies(
                                            rawData -> {
                                                assertThat(rawData.captionVectorId())
                                                        .isEqualTo("vec-1");
                                                assertThat(rawData.metadata())
                                                        .containsEntry("existing", "value")
                                                        .containsEntry("patched", "yes");
                                            });
                        });
    }

    @Test
    @DisplayName("upsertRawDataWithResources persists resource rows and raw-data references")
    void upsertRawDataWithResourcesPersistsResourceRowsAndRawDataReferences() {
        newContextRunner(tempDir.resolve("raw-data-with-resources.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);
                            var resource =
                                    new MemoryResource(
                                            "res-1",
                                            MEMORY_ID.toIdentifier(),
                                            "https://example.com/report.pdf",
                                            "file:///tmp/memind/report.pdf",
                                            "report.pdf",
                                            "application/pdf",
                                            "checksum-1",
                                            2048L,
                                            Map.of("pages", 3),
                                            BASE_TIME.minusSeconds(90));
                            var rawData =
                                    new MemoryRawData(
                                            "raw-1",
                                            MEMORY_ID.toIdentifier(),
                                            "DOCUMENT",
                                            "content-raw-1",
                                            Segment.single("hello multimodal"),
                                            "caption",
                                            null,
                                            Map.of("source", "document"),
                                            "res-1",
                                            "application/pdf",
                                            BASE_TIME,
                                            BASE_TIME,
                                            BASE_TIME.plusSeconds(60));

                            store.upsertRawDataWithResources(
                                    MEMORY_ID, List.of(resource), List.of(rawData));

                            assertThat(store.resourceOperations().getResource(MEMORY_ID, "res-1"))
                                    .contains(resource);
                            assertThat(store.rawDataOperations().getRawData(MEMORY_ID, "raw-1"))
                                    .get()
                                    .satisfies(
                                            persisted -> {
                                                assertThat(persisted.resourceId())
                                                        .isEqualTo("res-1");
                                                assertThat(persisted.mimeType())
                                                        .isEqualTo("application/pdf");
                                                assertThat(persisted.metadata())
                                                        .containsEntry("resourceId", "res-1")
                                                        .containsEntry(
                                                                "mimeType", "application/pdf");
                                            });
                        });
    }

    @Test
    @DisplayName("insertItems rolls back the whole batch when one insert fails")
    void insertItemsRollsBackWholeBatchWhenInsertFails() {
        newContextRunner(tempDir.resolve("add-items-tx.db"))
                .run(
                        context -> {
                            ItemOperations store = context.getBean(ItemOperations.class);
                            ItemOperations itemOps =
                                    context.getBean(MemoryStore.class).itemOperations();

                            assertThat(AopUtils.isAopProxy(store)).isTrue();

                            assertThatThrownBy(
                                            () ->
                                                    itemOps.insertItems(
                                                            MEMORY_ID,
                                                            List.of(
                                                                    memoryItem(1L, "first content"),
                                                                    memoryItem(
                                                                            1L,
                                                                            "duplicate content"))))
                                    .isInstanceOf(Exception.class);

                            assertThat(itemOps.listItems(MEMORY_ID)).isEmpty();
                        });
    }

    @Test
    @DisplayName("transactional commit uses batch item persistence instead of row fallback")
    void transactionalCommitUsesBatchItemPersistenceInsteadOfRowFallback() {
        newContextRunner(tempDir.resolve("graph-commit-batch-discipline.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);
                            MapperMethodCounter counter =
                                    context.getBean(MapperMethodCounter.class);

                            counter.reset();
                            store.itemGraphCommitOperations()
                                    .commit(
                                            MEMORY_ID,
                                            new ExtractionBatchId("batch-bulk"),
                                            List.of(
                                                    memoryItem(101L, "batch item 1"),
                                                    memoryItem(102L, "batch item 2"),
                                                    memoryItem(103L, "batch item 3")),
                                            ItemGraphWritePlan.builder().build());

                            assertThat(counter.count(MemoryItemMapper.class, "insertBatch"))
                                    .isEqualTo(1);
                            assertThat(counter.count(MemoryItemMapper.class, "insert")).isZero();
                            assertThat(counter.count(MemoryItemMapper.class, "selectList"))
                                    .isZero();
                        });
    }

    @Test
    @DisplayName("insertItems allows null occurredAt for non-temporal items")
    void insertItemsAllowsNullOccurredAtForNonTemporalItems() {
        newContextRunner(tempDir.resolve("null-occurred-at.db"))
                .run(
                        context -> {
                            ItemOperations itemOps =
                                    context.getBean(MemoryStore.class).itemOperations();

                            itemOps.insertItems(
                                    MEMORY_ID, List.of(memoryItem(10L, "stable preference", null)));

                            assertThat(itemOps.listItems(MEMORY_ID))
                                    .singleElement()
                                    .satisfies(
                                            item -> {
                                                assertThat(item.occurredAt()).isNull();
                                                assertThat(item.observedAt())
                                                        .isEqualTo(BASE_TIME.plusSeconds(30));
                                            });
                        });
    }

    @Test
    @DisplayName("insertItems round-trips structured temporal fields")
    void insertItemsRoundTripsStructuredTemporalFields() {
        newContextRunner(tempDir.resolve("structured-temporal-items.db"))
                .run(
                        context -> {
                            ItemOperations itemOps =
                                    context.getBean(MemoryStore.class).itemOperations();
                            Instant occurredAt = BASE_TIME.minusSeconds(120);
                            Instant occurredStart = BASE_TIME.minusSeconds(120);
                            Instant occurredEnd = BASE_TIME.plusSeconds(3600);

                            itemOps.insertItems(
                                    MEMORY_ID,
                                    List.of(
                                            memoryItem(
                                                    11L,
                                                    "temporal range item",
                                                    occurredAt,
                                                    occurredStart,
                                                    occurredEnd,
                                                    "range")));

                            assertThat(itemOps.getItemsByIds(MEMORY_ID, List.of(11L)))
                                    .singleElement()
                                    .satisfies(
                                            item -> {
                                                assertThat(item.occurredAt()).isEqualTo(occurredAt);
                                                assertThat(item.occurredStart())
                                                        .isEqualTo(occurredStart);
                                                assertThat(item.occurredEnd())
                                                        .isEqualTo(occurredEnd);
                                                assertThat(item.timeGranularity())
                                                        .isEqualTo("range");
                                            });
                        });
    }

    @Test
    @DisplayName("legacy rows fall back to occurredAt when structured temporal fields are empty")
    void legacyRowsFallbackToOccurredAtWhenStructuredTemporalFieldsAreEmpty() {
        newContextRunner(tempDir.resolve("legacy-temporal-items.db"))
                .run(
                        context -> {
                            ItemOperations itemOps =
                                    context.getBean(MemoryStore.class).itemOperations();
                            JdbcTemplate jdbcTemplate =
                                    new JdbcTemplate(context.getBean(DataSource.class));
                            Instant occurredAt = BASE_TIME.minusSeconds(1800);
                            Instant observedAt = BASE_TIME.plusSeconds(45);

                            jdbcTemplate.update(
                                    """
                                    INSERT INTO memory_item
                                        (biz_id, user_id, agent_id, memory_id, content, scope,
                                         category, vector_id, raw_data_id, content_hash, occurred_at,
                                         observed_at, type, raw_data_type, metadata, created_at,
                                         updated_at, deleted)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                                    12L,
                                    MEMORY_ID.getAttribute("userId"),
                                    MEMORY_ID.getAttribute("agentId"),
                                    MEMORY_ID.toIdentifier(),
                                    "legacy temporal item",
                                    MemoryScope.USER.name(),
                                    MemoryCategory.EVENT.name(),
                                    "vector-12",
                                    "raw-12",
                                    "hash-12",
                                    occurredAt.toString(),
                                    observedAt.toString(),
                                    MemoryItemType.FACT.name(),
                                    "conversation",
                                    "{\"legacy\":true}",
                                    BASE_TIME.toString(),
                                    BASE_TIME.toString(),
                                    0);

                            assertThat(itemOps.getItemsByIds(MEMORY_ID, List.of(12L)))
                                    .singleElement()
                                    .satisfies(
                                            item -> {
                                                assertThat(item.occurredAt()).isEqualTo(occurredAt);
                                                assertThat(item.occurredStart())
                                                        .isEqualTo(occurredAt);
                                                assertThat(item.occurredEnd()).isNull();
                                                assertThat(item.timeGranularity())
                                                        .isEqualTo("unknown");
                                                assertThat(item.observedAt()).isEqualTo(observedAt);
                                            });
                        });
    }

    @Test
    @DisplayName("insertItems persists derived temporal lookup columns")
    void insertItemsPersistsDerivedTemporalLookupColumns() {
        newContextRunner(tempDir.resolve("temporal-lookup-columns.db"))
                .run(
                        context -> {
                            ItemOperations itemOps =
                                    context.getBean(MemoryStore.class).itemOperations();
                            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));

                            itemOps.insertItems(
                                    MEMORY_ID,
                                    List.of(
                                            memoryItem(
                                                    21L,
                                                    "temporal item",
                                                    BASE_TIME,
                                                    BASE_TIME,
                                                    BASE_TIME.plusSeconds(3600),
                                                    "range")));

                            var row =
                                    jdbc.queryForMap(
                                            "SELECT temporal_start, temporal_end_or_anchor,"
                                                    + " temporal_anchor FROM memory_item WHERE"
                                                    + " biz_id = ?",
                                            21L);

                            assertThat(row.get("temporal_start").toString())
                                    .contains(BASE_TIME.toString());
                            assertThat(row.get("temporal_end_or_anchor").toString())
                                    .contains(BASE_TIME.plusSeconds(3600).toString());
                            assertThat(row.get("temporal_anchor").toString())
                                    .contains(BASE_TIME.toString());
                        });
    }

    @Test
    @DisplayName(
            "native temporal lookup returns overlap then before then after without deleted or"
                    + " mismatched rows")
    void nativeTemporalLookupReturnsBoundedMatches() {
        newContextRunner(tempDir.resolve("temporal-native-lookup.db"))
                .run(
                        context -> {
                            ItemOperations itemOps =
                                    context.getBean(MemoryStore.class).itemOperations();
                            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
                            MapperMethodCounter counter =
                                    context.getBean(MapperMethodCounter.class);

                            itemOps.insertItems(
                                    MEMORY_ID,
                                    List.of(
                                            memoryItem(
                                                    1L,
                                                    "before item",
                                                    BASE_TIME.minusSeconds(3600),
                                                    BASE_TIME.minusSeconds(3600),
                                                    null,
                                                    "instant"),
                                            memoryItem(
                                                    2L,
                                                    "overlap item",
                                                    BASE_TIME.minusSeconds(1800),
                                                    BASE_TIME.minusSeconds(1800),
                                                    BASE_TIME.plusSeconds(1800),
                                                    "range"),
                                            memoryItem(
                                                    3L,
                                                    "after item",
                                                    BASE_TIME.plusSeconds(3600),
                                                    BASE_TIME.plusSeconds(3600),
                                                    null,
                                                    "instant"),
                                            memoryItem(
                                                    4L,
                                                    "profile mismatch",
                                                    BASE_TIME.plusSeconds(300),
                                                    BASE_TIME.plusSeconds(300),
                                                    null,
                                                    "instant"),
                                            memoryItem(
                                                    5L,
                                                    "deleted item",
                                                    BASE_TIME.plusSeconds(7200),
                                                    BASE_TIME.plusSeconds(7200),
                                                    null,
                                                    "instant")));
                            jdbc.update(
                                    "UPDATE memory_item SET category = 'PROFILE' WHERE biz_id = ?",
                                    4L);
                            itemOps.deleteItems(MEMORY_ID, List.of(5L));
                            counter.reset();

                            var request =
                                    new TemporalCandidateRequest(
                                            101L,
                                            BASE_TIME,
                                            BASE_TIME,
                                            BASE_TIME,
                                            MemoryItemType.FACT,
                                            MemoryCategory.EVENT,
                                            4,
                                            8,
                                            8);

                            var matches =
                                    itemOps.listTemporalCandidateMatches(
                                            MEMORY_ID, List.of(request), Set.of(101L));

                            assertThat(matches)
                                    .extracting(match -> match.candidateItem().id())
                                    .containsExactly(2L, 1L, 3L);
                            assertThat(
                                            counter.count(
                                                    MemoryItemMapper.class,
                                                    "selectTemporalOverlapCandidates"))
                                    .isEqualTo(1);
                            assertThat(
                                            counter.count(
                                                    MemoryItemMapper.class,
                                                    "selectTemporalBeforeCandidates"))
                                    .isEqualTo(1);
                            assertThat(
                                            counter.count(
                                                    MemoryItemMapper.class,
                                                    "selectTemporalAfterCandidates"))
                                    .isEqualTo(1);
                            assertThat(counter.count(MemoryItemMapper.class, "selectList"))
                                    .isZero();
                        });
    }

    @Test
    @DisplayName("native temporal lookup matches uncategorized requests without throwing")
    void nativeTemporalLookupMatchesNullCategoryRequests() {
        newContextRunner(tempDir.resolve("temporal-native-null-category.db"))
                .run(
                        context -> {
                            ItemOperations itemOps =
                                    context.getBean(MemoryStore.class).itemOperations();
                            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));

                            itemOps.insertItems(
                                    MEMORY_ID,
                                    List.of(
                                            memoryItem(
                                                    6L,
                                                    "uncategorized item",
                                                    BASE_TIME.minusSeconds(900),
                                                    BASE_TIME.minusSeconds(900),
                                                    null,
                                                    "instant")));
                            jdbc.update(
                                    "UPDATE memory_item SET category = NULL WHERE biz_id = ?", 6L);

                            var request =
                                    new TemporalCandidateRequest(
                                            202L,
                                            BASE_TIME,
                                            BASE_TIME,
                                            BASE_TIME,
                                            MemoryItemType.FACT,
                                            null,
                                            4,
                                            8,
                                            8);

                            var matches =
                                    itemOps.listTemporalCandidateMatches(
                                            MEMORY_ID, List.of(request), Set.of(202L));

                            assertThat(matches)
                                    .extracting(match -> match.candidateItem().id())
                                    .containsExactly(6L);
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

    private static MemoryRawData rawData(String id, String caption, Map<String, Object> metadata) {
        return new MemoryRawData(
                id,
                MEMORY_ID.toIdentifier(),
                "conversation",
                "content-" + id,
                Segment.single("segment-" + id),
                caption,
                null,
                metadata,
                null,
                null,
                BASE_TIME,
                BASE_TIME,
                BASE_TIME.plusSeconds(60));
    }

    private static MemoryItem memoryItem(Long id, String content) {
        return memoryItem(id, content, BASE_TIME);
    }

    private static MemoryItem memoryItem(Long id, String content, Instant occurredAt) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                occurredAt,
                BASE_TIME.plusSeconds(30),
                Map.of("content", content),
                BASE_TIME,
                MemoryItemType.FACT);
    }

    private static MemoryItem memoryItem(
            Long id,
            String content,
            Instant occurredAt,
            Instant occurredStart,
            Instant occurredEnd,
            String timeGranularity) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                occurredAt,
                occurredStart,
                occurredEnd,
                timeGranularity,
                BASE_TIME.plusSeconds(30),
                Map.of("content", content),
                BASE_TIME,
                MemoryItemType.FACT);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({TransactionalTestConfig.class, SqliteTestSupportConfig.class})
    static class TestInfrastructureConfig {

        @Bean
        MapperMethodCounter mapperMethodCounter() {
            return new MapperMethodCounter();
        }

        @Bean
        BeanPostProcessor countingMapperBeanPostProcessor(MapperMethodCounter counter) {
            return new CountingMapperBeanPostProcessor(counter);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionalTestConfig {

        @Bean
        PlatformTransactionManager transactionManager(javax.sql.DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

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

    private static final class CountingMapperBeanPostProcessor implements BeanPostProcessor {

        private final MapperMethodCounter counter;

        private CountingMapperBeanPostProcessor(MapperMethodCounter counter) {
            this.counter = counter;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName)
                throws BeansException {
            if (!(bean instanceof MemoryRawDataMapper) && !(bean instanceof MemoryItemMapper)) {
                return bean;
            }

            Class<?>[] interfaces = bean.getClass().getInterfaces();
            if (interfaces.length == 0) {
                return bean;
            }

            return Proxy.newProxyInstance(
                    bean.getClass().getClassLoader(),
                    interfaces,
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return method.invoke(bean, args);
                        }
                        Class<?> mapperType =
                                bean instanceof MemoryRawDataMapper
                                        ? MemoryRawDataMapper.class
                                        : MemoryItemMapper.class;
                        counter.increment(mapperType, method.getName());
                        return method.invoke(bean, args);
                    });
        }
    }

    private static final class MapperMethodCounter {

        private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

        void increment(Class<?> mapperType, String methodName) {
            counts.computeIfAbsent(key(mapperType, methodName), ignored -> new AtomicInteger())
                    .incrementAndGet();
        }

        int count(Class<?> mapperType, String methodName) {
            return counts.getOrDefault(key(mapperType, methodName), new AtomicInteger()).get();
        }

        void reset() {
            counts.clear();
        }

        private String key(Class<?> mapperType, String methodName) {
            return mapperType.getName() + "#" + methodName;
        }
    }
}
