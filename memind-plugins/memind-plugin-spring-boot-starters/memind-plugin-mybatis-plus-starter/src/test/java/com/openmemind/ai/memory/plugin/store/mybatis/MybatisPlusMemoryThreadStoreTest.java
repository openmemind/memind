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
import com.openmemind.ai.memory.core.data.MemoryThread;
import com.openmemind.ai.memory.core.data.MemoryThreadItem;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadStatus;
import com.openmemind.ai.memory.core.store.MemoryStore;
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
import org.sqlite.SQLiteDataSource;

@DisplayName("MybatisPlus memory thread store")
class MybatisPlusMemoryThreadStoreTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant BASE_TIME = Instant.parse("2026-04-18T00:00:00Z");

    @TempDir Path tempDir;

    @Test
    @DisplayName("store round-trips thread rows and memberships")
    void storeRoundTripsThreadAndMemberships() {
        newContextRunner(tempDir.resolve("memory-thread-roundtrip.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);
                            MemoryThread thread = thread(101L, "ep:101");
                            MemoryThreadItem membership = membership(201L, 101L, 301L);

                            store.threadOperations().upsertThreads(MEMORY_ID, List.of(thread));
                            store.threadOperations()
                                    .upsertThreadItems(MEMORY_ID, List.of(membership));

                            assertThat(store.threadOperations().listThreads(MEMORY_ID))
                                    .extracting(MemoryThread::threadKey)
                                    .containsExactly("ep:101");
                            assertThat(store.threadOperations().listThreadItems(MEMORY_ID))
                                    .extracting(MemoryThreadItem::itemId)
                                    .containsExactly(301L);
                        });
    }

    @Test
    @DisplayName("store rejects multiple threads for the same item")
    void storeRejectsMultipleThreadsForSameItem() {
        newContextRunner(tempDir.resolve("memory-thread-invariant.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);

                            store.threadOperations()
                                    .upsertThreads(
                                            MEMORY_ID,
                                            List.of(
                                                    thread(101L, "ep:101"),
                                                    thread(102L, "ep:102")));
                            store.threadOperations()
                                    .upsertThreadItems(
                                            MEMORY_ID, List.of(membership(201L, 101L, 301L)));

                            assertThatThrownBy(
                                            () ->
                                                    store.threadOperations()
                                                            .upsertThreadItems(
                                                                    MEMORY_ID,
                                                                    List.of(
                                                                            membership(
                                                                                    202L, 102L,
                                                                                    301L))))
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("single-thread-per-item");
                        });
    }

    private ApplicationContextRunner newContextRunner(Path dbPath) {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                MemoryMybatisPlusAutoConfiguration.class,
                                MemorySchemaAutoConfiguration.class,
                                MybatisPlusAutoConfiguration.class))
                .withUserConfiguration(SqliteThreadStoreTestConfig.class)
                .withPropertyValues(
                        "test.sqlite.path=" + dbPath,
                        "memind.store.init-schema=true",
                        "spring.main.web-application-type=none");
    }

    private static MemoryThread thread(Long id, String threadKey) {
        return new MemoryThread(
                id,
                MEMORY_ID.toIdentifier(),
                threadKey,
                "recovery",
                "Recovery Thread",
                "Seed summary",
                MemoryThreadStatus.OPEN,
                0.90d,
                BASE_TIME,
                null,
                BASE_TIME,
                101L,
                101L,
                1,
                Map.of("source", "test"),
                BASE_TIME,
                BASE_TIME,
                false);
    }

    private static MemoryThreadItem membership(Long id, Long threadId, Long itemId) {
        return new MemoryThreadItem(
                id,
                MEMORY_ID.toIdentifier(),
                threadId,
                itemId,
                0.95d,
                MemoryThreadRole.CORE,
                1,
                BASE_TIME,
                Map.of("source", "test"),
                BASE_TIME,
                BASE_TIME,
                false);
    }

    @Configuration(proxyBeanMethods = false)
    static class SqliteThreadStoreTestConfig {

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
