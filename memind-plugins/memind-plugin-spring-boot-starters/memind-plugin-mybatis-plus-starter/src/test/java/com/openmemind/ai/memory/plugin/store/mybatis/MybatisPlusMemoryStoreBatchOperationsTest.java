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
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
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
