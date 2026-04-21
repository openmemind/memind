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

import static com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState.AVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadIntakeStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
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

@DisplayName("MybatisPlus memory thread projection store")
class MybatisPlusMemoryThreadStoreTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant BASE_TIME = Instant.parse("2026-04-20T00:00:00Z");

    @TempDir Path tempDir;

    @Test
    @DisplayName("store round-trips projection runtime and many-to-many memberships")
    void storeRoundTripsProjectionRuntimeAndManyToManyMemberships() {
        newContextRunner(tempDir.resolve("thread-v1-roundtrip.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);
                            var projectionStore = store.threadOperations();

                            projectionStore.replaceProjection(
                                    MEMORY_ID,
                                    List.of(projection("topic:topic:concept:travel")),
                                    List.of(event("topic:topic:concept:travel", 1L)),
                                    List.of(
                                            membership("topic:topic:concept:travel", 301L),
                                            membership("topic:topic:concept:travel", 302L)),
                                    runtime(AVAILABLE, 302L, 0, 0, false),
                                    BASE_TIME);

                            assertThat(
                                            projectionStore.listMemberships(
                                                    MEMORY_ID, "topic:topic:concept:travel"))
                                    .extracting(MemoryThreadMembership::itemId)
                                    .containsExactly(301L, 302L);
                            assertThat(projectionStore.listThreadsByItemId(MEMORY_ID, 302L))
                                    .extracting(MemoryThreadProjection::threadKey)
                                    .containsExactly("topic:topic:concept:travel");
                            assertThat(projectionStore.getRuntime(MEMORY_ID))
                                    .get()
                                    .extracting(
                                            MemoryThreadRuntimeState::projectionState,
                                            MemoryThreadRuntimeState::lastProcessedItemId)
                                    .containsExactly(AVAILABLE, 302L);
                        });
    }

    @Test
    @DisplayName("store persists outbox idempotency claim and finalization")
    void storePersistsOutboxIdempotencyClaimAndFinalization() {
        newContextRunner(tempDir.resolve("thread-v1-outbox.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);
                            var projectionStore = store.threadOperations();

                            projectionStore.ensureRuntime(MEMORY_ID, "thread-core-v1");
                            projectionStore.enqueue(MEMORY_ID, 301L);
                            projectionStore.enqueue(MEMORY_ID, 301L);
                            projectionStore.enqueue(MEMORY_ID, 302L);

                            assertThat(projectionStore.listOutbox(MEMORY_ID))
                                    .extracting(entry -> entry.triggerItemId())
                                    .containsExactly(301L, 302L);

                            var claimed =
                                    projectionStore.claimPending(
                                            MEMORY_ID,
                                            Instant.parse("2026-04-20T01:00:00Z"),
                                            Instant.parse("2026-04-20T01:05:00Z"),
                                            1);

                            assertThat(claimed)
                                    .singleElement()
                                    .extracting(
                                            entry -> entry.triggerItemId(), entry -> entry.status())
                                    .containsExactly(301L, MemoryThreadIntakeStatus.PROCESSING);

                            projectionStore.finalizeOutboxSuccess(
                                    MEMORY_ID, 301L, 301L, Instant.parse("2026-04-20T01:06:00Z"));

                            assertThat(projectionStore.listOutbox(MEMORY_ID))
                                    .filteredOn(entry -> entry.triggerItemId() == 301L)
                                    .singleElement()
                                    .extracting(entry -> entry.status())
                                    .isEqualTo(MemoryThreadIntakeStatus.COMPLETED);
                            assertThat(projectionStore.getRuntime(MEMORY_ID))
                                    .get()
                                    .extracting(
                                            MemoryThreadRuntimeState::pendingCount,
                                            MemoryThreadRuntimeState::lastProcessedItemId)
                                    .containsExactly(1L, 301L);
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

    private static MemoryThreadProjection projection(String threadKey) {
        return new MemoryThreadProjection(
                MEMORY_ID.toIdentifier(),
                threadKey,
                MemoryThreadType.TOPIC,
                "topic",
                "concept:travel",
                "Travel",
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.ONGOING,
                "Travel discussion",
                Map.of("source", "test"),
                1,
                BASE_TIME,
                BASE_TIME,
                BASE_TIME,
                null,
                1,
                2,
                BASE_TIME,
                BASE_TIME);
    }

    private static MemoryThreadEvent event(String threadKey, long eventSeq) {
        return new MemoryThreadEvent(
                MEMORY_ID.toIdentifier(),
                threadKey,
                threadKey + "#observation:" + eventSeq,
                eventSeq,
                com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType.OBSERVATION,
                BASE_TIME,
                Map.of("summary", "Observed travel discussion"),
                1,
                false,
                0.90d,
                BASE_TIME);
    }

    private static MemoryThreadMembership membership(String threadKey, long itemId) {
        return new MemoryThreadMembership(
                MEMORY_ID.toIdentifier(),
                threadKey,
                itemId,
                MemoryThreadMembershipRole.CORE,
                itemId == 301L,
                1.0d,
                BASE_TIME,
                BASE_TIME);
    }

    private static MemoryThreadRuntimeState runtime(
            MemoryThreadProjectionState projectionState,
            long lastProcessedItemId,
            long pendingCount,
            long failedCount,
            boolean rebuildInProgress) {
        return new MemoryThreadRuntimeState(
                MEMORY_ID.toIdentifier(),
                projectionState,
                pendingCount,
                failedCount,
                lastProcessedItemId,
                lastProcessedItemId,
                rebuildInProgress,
                null,
                "thread-core-v1",
                null,
                BASE_TIME);
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
