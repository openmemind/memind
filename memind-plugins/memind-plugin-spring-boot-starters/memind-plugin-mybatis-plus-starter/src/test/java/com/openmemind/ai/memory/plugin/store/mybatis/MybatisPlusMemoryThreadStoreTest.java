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
import static org.assertj.core.groups.Tuple.tuple;

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
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEnrichmentInput;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeClaim;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentAppendResult;
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
                                    runtime(AVAILABLE, 302L, 0, 0, false, 0L),
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
                                            MemoryThreadIntakeClaim::triggerItemId,
                                            MemoryThreadIntakeClaim::enqueueGeneration,
                                            MemoryThreadIntakeClaim::claimedAt,
                                            MemoryThreadIntakeClaim::leaseExpiresAt)
                                    .containsExactly(
                                            301L,
                                            1L,
                                            Instant.parse("2026-04-20T01:00:00Z"),
                                            Instant.parse("2026-04-20T01:05:00Z"));

                            projectionStore.commitClaimedIntakeReplaySuccess(
                                    MEMORY_ID,
                                    claimed,
                                    301L,
                                    List.of(projection("topic:topic:concept:travel")),
                                    List.of(event("topic:topic:concept:travel", 1L)),
                                    List.of(
                                            membership("topic:topic:concept:travel", 301L),
                                            membership("topic:topic:concept:travel", 302L)),
                                    runtime(AVAILABLE, 301L, 1, 0, false, 0L),
                                    Instant.parse("2026-04-20T01:06:00Z"));

                            assertThat(projectionStore.listOutbox(MEMORY_ID))
                                    .filteredOn(entry -> entry.triggerItemId() == 301L)
                                    .singleElement()
                                    .extracting(entry -> entry.status())
                                    .isEqualTo(MemoryThreadIntakeStatus.COMPLETED);
                            assertThat(projectionStore.listThreads(MEMORY_ID))
                                    .singleElement()
                                    .extracting(MemoryThreadProjection::threadKey)
                                    .isEqualTo("topic:topic:concept:travel");
                            assertThat(projectionStore.getRuntime(MEMORY_ID))
                                    .get()
                                    .extracting(
                                            MemoryThreadRuntimeState::pendingCount,
                                            MemoryThreadRuntimeState::lastProcessedItemId)
                                    .containsExactly(1L, 301L);
                        });
    }

    @Test
    @DisplayName("store preserves pending replay when an older claim completes")
    void mybatisStorePreservesPendingReplayWhenOlderClaimCompletes() {
        newContextRunner(tempDir.resolve("thread-v2-exact-claim.db"))
                .run(
                        context -> {
                            var projectionStore = context.getBean(MemoryStore.class).threadOperations();
                            projectionStore.ensureRuntime(MEMORY_ID, "thread-core-v2");
                            projectionStore.enqueue(MEMORY_ID, 301L);

                            MemoryThreadIntakeClaim firstClaim =
                                    projectionStore.claimPending(
                                                    MEMORY_ID,
                                                    Instant.parse("2026-04-23T01:00:00Z"),
                                                    Instant.parse("2026-04-23T01:00:30Z"),
                                                    1)
                                            .getFirst();
                            projectionStore.enqueueReplay(MEMORY_ID, 301L);

                            projectionStore.commitClaimedIntakeReplaySuccess(
                                    MEMORY_ID,
                                    List.of(firstClaim),
                                    301L,
                                    List.of(),
                                    List.of(),
                                    List.of(),
                                    runtime(AVAILABLE, 301L, 1, 0, false, 0L),
                                    Instant.parse("2026-04-23T01:01:00Z"));

                            assertThat(projectionStore.listOutbox(MEMORY_ID))
                                    .extracting(
                                            entry -> entry.triggerItemId(),
                                            entry -> entry.enqueueGeneration(),
                                            entry -> entry.status())
                                    .containsExactly(tuple(301L, 2L, MemoryThreadIntakeStatus.PENDING));
                        });
    }

    @Test
    @DisplayName("store round-trips milestone one projection evidence closure and weights")
    void storeRoundTripsMilestoneOneProjectionEvidenceClosureAndWeights() {
        newContextRunner(tempDir.resolve("thread-v1-milestone-one-roundtrip.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);
                            var projectionStore = store.threadOperations();
                            String threadKey = "topic:topic:concept:travel";
                            Map<String, Object> snapshotJson =
                                    Map.of(
                                            "facets",
                                            Map.of(
                                                    "evidence",
                                                    List.of(
                                                            Map.of(
                                                                    "family",
                                                                    "exact_anchor",
                                                                    "score",
                                                                    1.0d),
                                                            Map.of(
                                                                    "family",
                                                                    "two_hit_support",
                                                                    "score",
                                                                    0.82d))));
                            Instant closedAt = BASE_TIME.plusSeconds(1_800L);

                            projectionStore.replaceProjection(
                                    MEMORY_ID,
                                    List.of(projection(threadKey, snapshotJson, closedAt)),
                                    List.of(event(threadKey, 1L)),
                                    List.of(
                                            membership(threadKey, 301L, true, 1.0d),
                                            membership(threadKey, 302L, false, 0.82d)),
                                    runtime(AVAILABLE, 302L, 0, 0, false, 0L),
                                    BASE_TIME.plusSeconds(1_900L));

                            assertThat(projectionStore.getThread(MEMORY_ID, threadKey))
                                    .get()
                                    .satisfies(
                                            projection -> {
                                                assertThat(projection.snapshotJson())
                                                        .isEqualTo(snapshotJson);
                                                assertThat(projection.closedAt())
                                                        .isEqualTo(closedAt);
                                                assertThat(projection.memberCount()).isEqualTo(2L);
                                            });
                            assertThat(projectionStore.listMemberships(MEMORY_ID, threadKey))
                                    .extracting(
                                            MemoryThreadMembership::itemId,
                                            MemoryThreadMembership::primary,
                                            MemoryThreadMembership::relevanceWeight)
                                    .containsExactly(
                                            tuple(301L, true, 1.0d), tuple(302L, false, 0.82d));
                            assertThat(projectionStore.listThreadsByItemId(MEMORY_ID, 302L))
                                    .singleElement()
                                    .extracting(
                                            MemoryThreadProjection::threadKey,
                                            MemoryThreadProjection::closedAt)
                                    .containsExactly(threadKey, closedAt);
                        });
    }

    @Test
    @DisplayName("rebuild success skips replayed prefix and preserves later pending work")
    void rebuildSuccessSkipsReplayedPrefixAndPreservesLaterPendingWork() {
        newContextRunner(tempDir.resolve("thread-v1-rebuild-commit.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);
                            var projectionStore = store.threadOperations();
                            String threadKey = "topic:topic:concept:travel";

                            projectionStore.ensureRuntime(MEMORY_ID, "thread-core-v1");
                            projectionStore.enqueue(MEMORY_ID, 301L);
                            projectionStore.enqueue(MEMORY_ID, 302L);
                            projectionStore.enqueue(MEMORY_ID, 303L);

                            projectionStore.commitRebuildReplaySuccess(
                                    MEMORY_ID,
                                    302L,
                                    List.of(projection(threadKey)),
                                    List.of(event(threadKey, 1L), event(threadKey, 2L)),
                                    List.of(
                                            membership(threadKey, 301L, true, 1.0d),
                                            membership(threadKey, 302L, false, 0.78d)),
                                    runtime(AVAILABLE, 302L, 1, 0, false, 0L),
                                    Instant.parse("2026-04-20T01:10:00Z"));

                            assertThat(projectionStore.listOutbox(MEMORY_ID))
                                    .extracting(
                                            entry -> entry.triggerItemId(), entry -> entry.status())
                                    .containsExactly(
                                            tuple(301L, MemoryThreadIntakeStatus.SKIPPED),
                                            tuple(302L, MemoryThreadIntakeStatus.SKIPPED),
                                            tuple(303L, MemoryThreadIntakeStatus.PENDING));
                            assertThat(projectionStore.listThreads(MEMORY_ID))
                                    .singleElement()
                                    .extracting(
                                            MemoryThreadProjection::threadKey,
                                            MemoryThreadProjection::memberCount)
                                    .containsExactly(threadKey, 2L);
                            assertThat(projectionStore.getRuntime(MEMORY_ID))
                                    .get()
                                    .extracting(
                                            MemoryThreadRuntimeState::projectionState,
                                            MemoryThreadRuntimeState::pendingCount,
                                            MemoryThreadRuntimeState::lastProcessedItemId)
                                    .containsExactly(AVAILABLE, 1L, 302L);
                        });
    }

    @Test
    @DisplayName("store round-trips authoritative enrichment inputs in replay order")
    void storeRoundTripsAuthoritativeEnrichmentInputsInReplayOrder() {
        newContextRunner(tempDir.resolve("thread-v1-enrichment-inputs.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);
                            var inputStore = store.threadEnrichmentInputStore();

                            assertThat(
                                            inputStore.appendRunAndEnqueueReplay(
                                                    MEMORY_ID,
                                                    402L,
                                                    List.of(
                                                            enrichmentInput(
                                                                    "topic:topic:concept:travel",
                                                                    "topic:topic:concept:travel|402|3|thread-core-v1",
                                                                    1,
                                                                    402L,
                                                                    3L,
                                                                    BASE_TIME.plusSeconds(20)),
                                                            enrichmentInput(
                                                                    "topic:topic:concept:travel",
                                                                    "topic:topic:concept:travel|402|3|thread-core-v1",
                                                                    0,
                                                                    402L,
                                                                    3L,
                                                                    BASE_TIME.plusSeconds(30)))))
                                    .isEqualTo(ThreadEnrichmentAppendResult.INSERTED);
                            assertThat(
                                            inputStore.appendRunAndEnqueueReplay(
                                                    MEMORY_ID,
                                                    401L,
                                                    List.of(
                                                            enrichmentInput(
                                                                    "topic:topic:concept:travel",
                                                                    "topic:topic:concept:travel|401|2|thread-core-v1",
                                                                    0,
                                                                    401L,
                                                                    2L,
                                                                    BASE_TIME.plusSeconds(40)))))
                                    .isEqualTo(ThreadEnrichmentAppendResult.INSERTED);
                            inputStore.appendRunAndEnqueueReplay(
                                    MEMORY_ID,
                                    402L,
                                    List.of(
                                            enrichmentInput(
                                                    "topic:topic:concept:travel",
                                                    "topic:topic:concept:travel|402|3|legacy-policy",
                                                    0,
                                                    402L,
                                                    3L,
                                                    BASE_TIME.plusSeconds(10),
                                                    "legacy-policy")));

                            assertThat(inputStore.listReplayable(MEMORY_ID, 402L, "thread-core-v1"))
                                    .extracting(
                                            MemoryThreadEnrichmentInput::basisCutoffItemId,
                                            MemoryThreadEnrichmentInput::basisMeaningfulEventCount,
                                            MemoryThreadEnrichmentInput::inputRunKey,
                                            MemoryThreadEnrichmentInput::entrySeq)
                                    .containsExactly(
                                            tuple(
                                                    401L,
                                                    2L,
                                                    "topic:topic:concept:travel|401|2|thread-core-v1",
                                                    0),
                                            tuple(
                                                    402L,
                                                    3L,
                                                    "topic:topic:concept:travel|402|3|thread-core-v1",
                                                    0),
                                            tuple(
                                                    402L,
                                                    3L,
                                                    "topic:topic:concept:travel|402|3|thread-core-v1",
                                                    1));
                            assertThat(store.threadOperations().listOutbox(MEMORY_ID))
                                    .extracting(
                                            entry -> entry.triggerItemId(), entry -> entry.status())
                                    .containsExactly(
                                            tuple(401L, MemoryThreadIntakeStatus.PENDING),
                                            tuple(402L, MemoryThreadIntakeStatus.PENDING));
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
        return projection(threadKey, Map.of("source", "test"), null);
    }

    private static MemoryThreadProjection projection(
            String threadKey, Map<String, Object> snapshotJson, Instant closedAt) {
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
                snapshotJson,
                1,
                BASE_TIME,
                BASE_TIME,
                BASE_TIME,
                closedAt,
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
        return membership(threadKey, itemId, itemId == 301L, 1.0d);
    }

    private static MemoryThreadMembership membership(
            String threadKey, long itemId, boolean primary, double relevanceWeight) {
        return new MemoryThreadMembership(
                MEMORY_ID.toIdentifier(),
                threadKey,
                itemId,
                MemoryThreadMembershipRole.CORE,
                primary,
                relevanceWeight,
                BASE_TIME,
                BASE_TIME);
    }

    private static MemoryThreadRuntimeState runtime(
            MemoryThreadProjectionState projectionState,
            long lastProcessedItemId,
            long pendingCount,
            long failedCount,
            boolean rebuildInProgress,
            long rebuildEpoch) {
        return new MemoryThreadRuntimeState(
                MEMORY_ID.toIdentifier(),
                projectionState,
                pendingCount,
                failedCount,
                lastProcessedItemId,
                lastProcessedItemId,
                rebuildInProgress,
                null,
                rebuildEpoch,
                "thread-core-v1",
                null,
                BASE_TIME);
    }

    private static MemoryThreadEnrichmentInput enrichmentInput(
            String threadKey,
            String inputRunKey,
            int entrySeq,
            long basisCutoffItemId,
            long basisMeaningfulEventCount,
            Instant createdAt) {
        return enrichmentInput(
                threadKey,
                inputRunKey,
                entrySeq,
                basisCutoffItemId,
                basisMeaningfulEventCount,
                createdAt,
                "thread-core-v1");
    }

    private static MemoryThreadEnrichmentInput enrichmentInput(
            String threadKey,
            String inputRunKey,
            int entrySeq,
            long basisCutoffItemId,
            long basisMeaningfulEventCount,
            Instant createdAt,
            String policyVersion) {
        return new MemoryThreadEnrichmentInput(
                MEMORY_ID.toIdentifier(),
                threadKey,
                inputRunKey,
                entrySeq,
                basisCutoffItemId,
                basisMeaningfulEventCount,
                policyVersion,
                Map.of(
                        "eventType",
                        "OBSERVATION",
                        "meaningful",
                        false,
                        "basisEventKey",
                        threadKey + ":observation:" + basisCutoffItemId,
                        "summary",
                        "Travel headline refresh " + entrySeq,
                        "summaryRole",
                        "HEADLINE_REFRESH"),
                Map.of(
                        "sourceType",
                        "THREAD_LLM",
                        "supportingItemIds",
                        List.of(basisCutoffItemId)),
                createdAt);
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
