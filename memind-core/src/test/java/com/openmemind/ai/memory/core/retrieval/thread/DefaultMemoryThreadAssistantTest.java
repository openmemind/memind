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
package com.openmemind.ai.memory.core.retrieval.thread;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DefaultMemoryThreadAssistantTest {

    private static final MemoryId MEMORY_ID = TestMemoryIds.userAgent();
    private static final Instant BASE_TIME = Instant.parse("2026-04-18T00:00:00Z");
    private static final Instant FIXED_NOW = Instant.parse("2026-04-22T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final Duration DORMANT_AFTER = Duration.ofDays(10);

    @Test
    void assistantSeedsFromTopThreeDirectItemHitsOnly() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        insertItems(
                store,
                item(101L, BASE_TIME.plusSeconds(10)),
                item(102L, BASE_TIME.plusSeconds(20)),
                item(103L, BASE_TIME.plusSeconds(30)),
                item(104L, BASE_TIME.plusSeconds(40)),
                item(201L, BASE_TIME.plusSeconds(15)),
                item(301L, BASE_TIME.plusSeconds(25)),
                item(401L, BASE_TIME.plusSeconds(35)),
                item(501L, BASE_TIME.plusSeconds(45)));
        replaceProjection(
                store,
                List.of(
                        projection(
                                "topic:alpha",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(15),
                                null,
                                2),
                        projection(
                                "topic:beta",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(25),
                                null,
                                2),
                        projection(
                                "topic:gamma",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(35),
                                null,
                                2),
                        projection(
                                "topic:omega",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(45),
                                null,
                                2)),
                List.of(
                        eventWithItemSource("topic:alpha", 1L, 101L, BASE_TIME.plusSeconds(10)),
                        eventWithItemSource("topic:alpha", 2L, 201L, BASE_TIME.plusSeconds(15)),
                        eventWithItemSource("topic:beta", 1L, 102L, BASE_TIME.plusSeconds(20)),
                        eventWithItemSource("topic:beta", 2L, 301L, BASE_TIME.plusSeconds(25)),
                        eventWithItemSource("topic:gamma", 1L, 103L, BASE_TIME.plusSeconds(30)),
                        eventWithItemSource("topic:gamma", 2L, 401L, BASE_TIME.plusSeconds(35)),
                        eventWithItemSource("topic:omega", 1L, 104L, BASE_TIME.plusSeconds(40)),
                        eventWithItemSource("topic:omega", 2L, 501L, BASE_TIME.plusSeconds(45))),
                List.of(
                        membership("topic:alpha", 101L, true, 1.0d),
                        membership("topic:alpha", 201L, false, 0.81d),
                        membership("topic:beta", 102L, true, 1.0d),
                        membership("topic:beta", 301L, false, 0.80d),
                        membership("topic:gamma", 103L, true, 1.0d),
                        membership("topic:gamma", 401L, false, 0.79d),
                        membership("topic:omega", 104L, true, 1.0d),
                        membership("topic:omega", 501L, false, 0.78d)));

        StepVerifier.create(
                        assistant(store)
                                .assist(
                                        context(),
                                        RetrievalConfig.simple(),
                                        settings(true, 4, 1, 1, Duration.ofMillis(200)),
                                        List.of(
                                                scored("101", 0.95d),
                                                scored("102", 0.90d),
                                                scored("103", 0.85d),
                                                scored("104", 0.80d))))
                .assertNext(
                        result -> {
                            assertThat(result.items())
                                    .extracting(ScoredResult::sourceId)
                                    .containsExactly("101", "201", "301", "401");
                            assertThat(result.items())
                                    .extracting(ScoredResult::sourceId)
                                    .doesNotContain("501");
                            assertThat(result.stats().seedThreadCount()).isEqualTo(3);
                            assertThat(result.stats().candidateCount()).isEqualTo(3);
                            assertThat(result.stats().admittedMemberCount()).isEqualTo(3);
                            assertThat(result.stats().clamped()).isTrue();
                        })
                .verifyComplete();
    }

    @Test
    void threadRankingPrefersSeedCoverageThenBestDirectScoreThenStateBucket() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        insertItems(
                store,
                item(101L, BASE_TIME.plusSeconds(10)),
                item(102L, BASE_TIME.plusSeconds(20)),
                item(103L, BASE_TIME.plusSeconds(30)),
                item(201L, BASE_TIME.plusSeconds(25)),
                item(301L, BASE_TIME.plusSeconds(35)),
                item(401L, BASE_TIME.plusSeconds(36)));
        replaceProjection(
                store,
                List.of(
                        projection(
                                "topic:coverage",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(25),
                                null,
                                3),
                        projection(
                                "topic:state-active",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(35),
                                null,
                                2),
                        projection(
                                "topic:state-closed",
                                MemoryThreadLifecycleStatus.CLOSED,
                                MemoryThreadObjectState.RESOLVED,
                                BASE_TIME.plusSeconds(36),
                                BASE_TIME.plusSeconds(36),
                                2)),
                List.of(
                        eventWithItemSource("topic:coverage", 1L, 101L, BASE_TIME.plusSeconds(10)),
                        eventWithItemSource("topic:coverage", 2L, 102L, BASE_TIME.plusSeconds(20)),
                        eventWithItemSource("topic:coverage", 3L, 201L, BASE_TIME.plusSeconds(25)),
                        eventWithItemSource(
                                "topic:state-active", 1L, 103L, BASE_TIME.plusSeconds(30)),
                        eventWithItemSource(
                                "topic:state-active", 2L, 301L, BASE_TIME.plusSeconds(35)),
                        eventWithItemSource(
                                "topic:state-closed", 1L, 103L, BASE_TIME.plusSeconds(30)),
                        eventWithItemSource(
                                "topic:state-closed", 2L, 401L, BASE_TIME.plusSeconds(36))),
                List.of(
                        membership("topic:coverage", 101L, true, 1.0d),
                        membership("topic:coverage", 102L, false, 1.0d),
                        membership("topic:coverage", 201L, false, 0.81d),
                        membership("topic:state-active", 103L, true, 1.0d),
                        membership("topic:state-active", 301L, false, 0.80d),
                        membership("topic:state-closed", 103L, false, 1.0d),
                        membership("topic:state-closed", 401L, false, 0.80d)));

        StepVerifier.create(
                        assistant(store)
                                .assist(
                                        context(),
                                        RetrievalConfig.simple(),
                                        settings(true, 3, 1, 0, Duration.ofMillis(200)),
                                        List.of(
                                                scored("101", 0.85d),
                                                scored("102", 0.84d),
                                                scored("103", 0.99d))))
                .assertNext(
                        result ->
                                assertThat(result.items())
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("201", "301", "401"))
                .verifyComplete();
    }

    @Test
    void threadRankingFallsBackDeterministicallyToThreadKeyWhenEarlierKeysTie() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        insertItems(
                store,
                item(101L, BASE_TIME.plusSeconds(10)),
                item(201L, BASE_TIME.plusSeconds(20)),
                item(301L, BASE_TIME.plusSeconds(20)));
        replaceProjection(
                store,
                List.of(
                        projection(
                                "topic:alpha",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(20),
                                null,
                                2),
                        projection(
                                "topic:beta",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(20),
                                null,
                                2)),
                List.of(
                        eventWithItemSource("topic:alpha", 1L, 101L, BASE_TIME.plusSeconds(10)),
                        eventWithItemSource("topic:alpha", 2L, 201L, BASE_TIME.plusSeconds(20)),
                        eventWithItemSource("topic:beta", 1L, 101L, BASE_TIME.plusSeconds(10)),
                        eventWithItemSource("topic:beta", 2L, 301L, BASE_TIME.plusSeconds(20))),
                List.of(
                        membership("topic:alpha", 101L, true, 1.0d),
                        membership("topic:alpha", 201L, false, 0.80d),
                        membership("topic:beta", 101L, false, 1.0d),
                        membership("topic:beta", 301L, false, 0.80d)));

        StepVerifier.create(
                        assistant(store)
                                .assist(
                                        context(),
                                        RetrievalConfig.simple(),
                                        settings(true, 2, 1, 0, Duration.ofMillis(200)),
                                        List.of(scored("101", 0.95d), scored("999", 0.20d))))
                .assertNext(
                        result ->
                                assertThat(result.items())
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("201", "301"))
                .verifyComplete();
    }

    @Test
    void threadRankingUsesRemainingEligibleNonDirectMemberCountBeforeAdmissionCap() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        insertItems(
                store,
                item(101L, BASE_TIME.plusSeconds(10)),
                item(201L, BASE_TIME.plusSeconds(20)),
                item(202L, BASE_TIME.plusSeconds(21)),
                item(203L, BASE_TIME.plusSeconds(22)),
                item(301L, BASE_TIME.plusSeconds(20)),
                item(302L, BASE_TIME.plusSeconds(21)));
        replaceProjection(
                store,
                List.of(
                        projection(
                                "topic:zeta",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(22),
                                null,
                                4),
                        projection(
                                "topic:alpha",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(21),
                                null,
                                3)),
                List.of(
                        eventWithItemSource("topic:zeta", 1L, 101L, BASE_TIME.plusSeconds(10)),
                        eventWithItemSource("topic:zeta", 2L, 201L, BASE_TIME.plusSeconds(20)),
                        eventWithItemSource("topic:zeta", 3L, 202L, BASE_TIME.plusSeconds(21)),
                        eventWithItemSource("topic:zeta", 4L, 203L, BASE_TIME.plusSeconds(22)),
                        eventWithItemSource("topic:alpha", 1L, 101L, BASE_TIME.plusSeconds(10)),
                        eventWithItemSource("topic:alpha", 2L, 301L, BASE_TIME.plusSeconds(20)),
                        eventWithItemSource("topic:alpha", 3L, 302L, BASE_TIME.plusSeconds(21))),
                List.of(
                        membership("topic:zeta", 101L, true, 1.0d),
                        membership("topic:zeta", 201L, false, 0.82d),
                        membership("topic:zeta", 202L, false, 0.81d),
                        membership("topic:zeta", 203L, false, 0.80d),
                        membership("topic:alpha", 101L, false, 1.0d),
                        membership("topic:alpha", 301L, false, 0.82d),
                        membership("topic:alpha", 302L, false, 0.81d)));

        StepVerifier.create(
                        assistant(store)
                                .assist(
                                        context(),
                                        RetrievalConfig.simple(),
                                        settings(true, 2, 1, 0, Duration.ofMillis(200)),
                                        List.of(scored("101", 0.95d), scored("999", 0.20d))))
                .assertNext(
                        result ->
                                assertThat(result.items())
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("201", "301"))
                .verifyComplete();
    }

    @Test
    void resolvedThreadWithoutClosedAtFallsBackToLastEventAtForRecentClosedRanking() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        insertItems(
                store,
                item(101L, BASE_TIME.plusSeconds(10)),
                item(201L, FIXED_NOW.minus(Duration.ofDays(1))),
                item(301L, FIXED_NOW.minus(Duration.ofDays(1))));
        replaceProjection(
                store,
                List.of(
                        projection(
                                "topic:resolved-recent",
                                MemoryThreadLifecycleStatus.CLOSED,
                                MemoryThreadObjectState.RESOLVED,
                                FIXED_NOW.minus(Duration.ofDays(1)),
                                null,
                                2),
                        projection(
                                "topic:dormant",
                                MemoryThreadLifecycleStatus.DORMANT,
                                MemoryThreadObjectState.STABLE,
                                FIXED_NOW.minus(Duration.ofDays(1)),
                                null,
                                2)),
                List.of(
                        eventWithItemSource(
                                "topic:resolved-recent", 1L, 101L, BASE_TIME.plusSeconds(10)),
                        eventWithItemSource(
                                "topic:resolved-recent",
                                2L,
                                201L,
                                FIXED_NOW.minus(Duration.ofDays(1))),
                        eventWithItemSource("topic:dormant", 1L, 101L, BASE_TIME.plusSeconds(10)),
                        eventWithItemSource(
                                "topic:dormant", 2L, 301L, FIXED_NOW.minus(Duration.ofDays(1)))),
                List.of(
                        membership("topic:resolved-recent", 101L, true, 1.0d),
                        membership("topic:resolved-recent", 201L, false, 0.80d),
                        membership("topic:dormant", 101L, false, 1.0d),
                        membership("topic:dormant", 301L, false, 0.80d)));

        StepVerifier.create(
                        assistant(store)
                                .assist(
                                        context(),
                                        RetrievalConfig.simple(),
                                        settings(true, 2, 1, 0, Duration.ofMillis(200)),
                                        List.of(scored("101", 0.95d), scored("999", 0.20d))))
                .assertNext(
                        result ->
                                assertThat(result.items())
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("201", "301"))
                .verifyComplete();
    }

    @Test
    void memberRankingUsesWeightThenSeedProximityThenRecencyThenItemId() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        insertItems(
                store,
                item(101L, BASE_TIME.plusSeconds(10)),
                item(201L, BASE_TIME.plusSeconds(100)),
                item(202L, BASE_TIME.plusSeconds(80)),
                item(203L, BASE_TIME.plusSeconds(60)),
                item(204L, BASE_TIME.plusSeconds(60)));
        replaceProjection(
                store,
                List.of(
                        projection(
                                "topic:ranking",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(100),
                                null,
                                5)),
                List.of(
                        eventWithItemSource("topic:ranking", 5L, 101L, BASE_TIME.plusSeconds(10)),
                        eventWithItemSource("topic:ranking", 10L, 201L, BASE_TIME.plusSeconds(100)),
                        eventWithItemSource("topic:ranking", 6L, 202L, BASE_TIME.plusSeconds(80)),
                        eventWithItemSource("topic:ranking", 6L, 203L, BASE_TIME.plusSeconds(60)),
                        eventWithItemSource("topic:ranking", 6L, 204L, BASE_TIME.plusSeconds(60))),
                List.of(
                        membership("topic:ranking", 101L, true, 1.0d),
                        membership("topic:ranking", 201L, false, 0.95d),
                        membership("topic:ranking", 202L, false, 0.90d),
                        membership("topic:ranking", 203L, false, 0.90d),
                        membership("topic:ranking", 204L, false, 0.90d)));

        StepVerifier.create(
                        assistant(store)
                                .assist(
                                        context(),
                                        RetrievalConfig.simple(),
                                        settings(true, 1, 4, 0, Duration.ofMillis(200)),
                                        List.of(
                                                scored("101", 0.95d),
                                                scored("999", 0.20d),
                                                scored("998", 0.19d),
                                                scored("997", 0.18d))))
                .assertNext(
                        result ->
                                assertThat(result.items())
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("201", "202", "203", "204"))
                .verifyComplete();
    }

    @Test
    void missingMemberRecencyRanksBehindKnownRecencyThenFallsThroughToItemId() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        insertItems(
                store,
                item(101L, null),
                item(201L, BASE_TIME.plusSeconds(50)),
                itemWithoutAnyEventTime(202L),
                itemWithoutAnyEventTime(203L));
        replaceProjection(
                store,
                List.of(
                        projection(
                                "topic:recency",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(50),
                                null,
                                4)),
                List.of(
                        eventWithoutItemSource("topic:recency", 1L, BASE_TIME.plusSeconds(10)),
                        eventWithoutItemSource("topic:recency", 2L, BASE_TIME.plusSeconds(20))),
                List.of(
                        membership("topic:recency", 101L, true, 1.0d),
                        membership("topic:recency", 201L, false, 0.80d),
                        membership("topic:recency", 202L, false, 0.80d),
                        membership("topic:recency", 203L, false, 0.80d)));

        StepVerifier.create(
                        assistant(store)
                                .assist(
                                        context(),
                                        RetrievalConfig.simple(),
                                        settings(true, 1, 3, 0, Duration.ofMillis(200)),
                                        List.of(
                                                scored("101", 0.95d),
                                                scored("999", 0.20d),
                                                scored("998", 0.19d))))
                .assertNext(
                        result ->
                                assertThat(result.items())
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("201", "202", "203"))
                .verifyComplete();
    }

    @Test
    void seedProximityFallsBackDeterministicallyWhenSequenceMappingIsUnavailable() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        insertItems(
                store,
                item(101L, BASE_TIME.plusSeconds(10)),
                item(201L, BASE_TIME.plusSeconds(15)),
                item(202L, BASE_TIME.plusSeconds(70)));
        replaceProjection(
                store,
                List.of(
                        projection(
                                "topic:time-fallback",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(70),
                                null,
                                3)),
                List.of(
                        eventWithoutItemSource(
                                "topic:time-fallback", 1L, BASE_TIME.plusSeconds(10)),
                        eventWithoutItemSource(
                                "topic:time-fallback", 2L, BASE_TIME.plusSeconds(15)),
                        eventWithoutItemSource(
                                "topic:time-fallback", 3L, BASE_TIME.plusSeconds(70))),
                List.of(
                        membership("topic:time-fallback", 101L, true, 1.0d),
                        membership("topic:time-fallback", 201L, false, 0.80d),
                        membership("topic:time-fallback", 202L, false, 0.80d)));

        StepVerifier.create(
                        assistant(store)
                                .assist(
                                        context(),
                                        RetrievalConfig.simple(),
                                        settings(true, 1, 2, 0, Duration.ofMillis(200)),
                                        List.of(scored("101", 0.95d), scored("999", 0.20d))))
                .assertNext(
                        result ->
                                assertThat(result.items())
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("201", "202"))
                .verifyComplete();
    }

    @Test
    void timeoutOrFailureFallsBackToDirectOnlyAndMarksStatsDegraded() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        insertItems(
                store,
                item(101L, BASE_TIME.plusSeconds(10)),
                item(102L, BASE_TIME.plusSeconds(20)));
        List<ScoredResult> directWindow = List.of(scored("101", 0.95d), scored("102", 0.90d));

        StepVerifier.create(
                        assistant(store, new SlowThreadRanker(store, DORMANT_AFTER, FIXED_CLOCK))
                                .assist(
                                        context(),
                                        RetrievalConfig.simple(),
                                        settings(true, 1, 1, 0, Duration.ofMillis(10)),
                                        directWindow))
                .assertNext(
                        result -> {
                            assertThat(result.items()).isEqualTo(directWindow);
                            assertThat(result.stats().degraded()).isTrue();
                            assertThat(result.stats().timedOut()).isTrue();
                        })
                .verifyComplete();

        StepVerifier.create(
                        assistant(
                                        store,
                                        new ExplodingThreadRanker(
                                                store, DORMANT_AFTER, FIXED_CLOCK))
                                .assist(
                                        context(),
                                        RetrievalConfig.simple(),
                                        settings(true, 1, 1, 0, Duration.ofMillis(50)),
                                        directWindow))
                .assertNext(
                        result -> {
                            assertThat(result.items()).isEqualTo(directWindow);
                            assertThat(result.stats().degraded()).isTrue();
                            assertThat(result.stats().timedOut()).isFalse();
                        })
                .verifyComplete();
    }

    @Test
    void reboundKeepsPinnedPrefixStableWhenThreadOverlapProducesDuplicates() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        insertItems(
                store,
                item(101L, BASE_TIME.plusSeconds(10)),
                item(102L, BASE_TIME.plusSeconds(20)),
                item(103L, BASE_TIME.plusSeconds(30)),
                item(201L, BASE_TIME.plusSeconds(35)));
        replaceProjection(
                store,
                List.of(
                        projection(
                                "topic:alpha",
                                MemoryThreadLifecycleStatus.ACTIVE,
                                MemoryThreadObjectState.ONGOING,
                                BASE_TIME.plusSeconds(35),
                                null,
                                4)),
                List.of(
                        eventWithItemSource("topic:alpha", 1L, 101L, BASE_TIME.plusSeconds(10)),
                        eventWithItemSource("topic:alpha", 2L, 102L, BASE_TIME.plusSeconds(20)),
                        eventWithItemSource("topic:alpha", 3L, 103L, BASE_TIME.plusSeconds(30)),
                        eventWithItemSource("topic:alpha", 4L, 201L, BASE_TIME.plusSeconds(35))),
                List.of(
                        membership("topic:alpha", 101L, true, 1.0d),
                        membership("topic:alpha", 102L, false, 1.0d),
                        membership("topic:alpha", 103L, false, 0.80d),
                        membership("topic:alpha", 201L, false, 0.79d)));

        StepVerifier.create(
                        assistant(store)
                                .assist(
                                        context(),
                                        RetrievalConfig.simple(),
                                        settings(true, 1, 4, 2, Duration.ofMillis(200)),
                                        List.of(
                                                scored("101", 0.95d),
                                                scored("102", 0.90d),
                                                scored("103", 0.85d))))
                .assertNext(
                        result -> {
                            assertThat(result.items())
                                    .extracting(ScoredResult::sourceId)
                                    .containsExactly("101", "102", "201");
                            assertThat(result.stats().admittedMemberCount()).isEqualTo(1);
                        })
                .verifyComplete();
    }

    private static DefaultMemoryThreadAssistant assistant(InMemoryMemoryStore store) {
        return new DefaultMemoryThreadAssistant(store, DORMANT_AFTER, FIXED_CLOCK);
    }

    private static DefaultMemoryThreadAssistant assistant(
            InMemoryMemoryStore store, ThreadAssistThreadRanker ranker) {
        return new DefaultMemoryThreadAssistant(
                store,
                DORMANT_AFTER,
                FIXED_CLOCK,
                new ThreadAssistSeedResolver(),
                ranker,
                new ThreadAssistMemberRanker(store));
    }

    private static QueryContext context() {
        return new QueryContext(
                MEMORY_ID,
                "what happened after that conversation",
                null,
                List.of(),
                Map.of(),
                null,
                null);
    }

    private static TestThreadSettings settings(
            boolean enabled,
            int maxThreads,
            int maxMembersPerThread,
            int protectDirectTopK,
            Duration timeout) {
        return new TestThreadSettings(
                enabled, maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
    }

    private static void insertItems(InMemoryMemoryStore store, MemoryItem... items) {
        store.itemOperations().insertItems(MEMORY_ID, List.of(items));
    }

    private static void replaceProjection(
            InMemoryMemoryStore store,
            List<MemoryThreadProjection> projections,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships) {
        store.threadOperations()
                .replaceProjection(MEMORY_ID, projections, events, memberships, null, FIXED_NOW);
    }

    private static ScoredResult scored(String sourceId, double score) {
        return new ScoredResult(
                ScoredResult.SourceType.ITEM, sourceId, "item-" + sourceId, 0.8f, score);
    }

    private static MemoryThreadProjection projection(
            String threadKey,
            MemoryThreadLifecycleStatus lifecycleStatus,
            MemoryThreadObjectState objectState,
            Instant lastEventAt,
            Instant closedAt,
            long memberCount) {
        return new MemoryThreadProjection(
                MEMORY_ID.toIdentifier(),
                threadKey,
                MemoryThreadType.TOPIC,
                "topic",
                threadKey.substring(threadKey.indexOf(':') + 1),
                threadKey,
                lifecycleStatus,
                objectState,
                "headline-" + threadKey,
                Map.of(),
                1,
                BASE_TIME,
                lastEventAt,
                lastEventAt,
                closedAt,
                memberCount,
                memberCount,
                BASE_TIME,
                BASE_TIME);
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

    private static MemoryThreadEvent eventWithItemSource(
            String threadKey, long eventSeq, long itemId, Instant eventTime) {
        return new MemoryThreadEvent(
                MEMORY_ID.toIdentifier(),
                threadKey,
                threadKey + "#" + eventSeq + ":" + itemId,
                eventSeq,
                MemoryThreadEventType.OBSERVATION,
                eventTime,
                Map.of(
                        "summary",
                        "event-" + itemId,
                        "sources",
                        List.of(Map.of("sourceType", "ITEM", "itemId", itemId))),
                1,
                false,
                0.9d,
                eventTime);
    }

    private static MemoryThreadEvent eventWithoutItemSource(
            String threadKey, long eventSeq, Instant eventTime) {
        return new MemoryThreadEvent(
                MEMORY_ID.toIdentifier(),
                threadKey,
                threadKey + "#" + eventSeq,
                eventSeq,
                MemoryThreadEventType.OBSERVATION,
                eventTime,
                Map.of("summary", "annotation-only"),
                1,
                false,
                0.9d,
                eventTime);
    }

    private static MemoryItem item(long id, Instant occurredAt) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "item-" + id,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
                "conversation",
                "vec-" + id,
                "raw-" + id,
                "hash-" + id,
                occurredAt,
                occurredAt,
                Map.of(),
                occurredAt != null ? occurredAt : BASE_TIME,
                MemoryItemType.FACT);
    }

    private static MemoryItem itemWithoutAnyEventTime(long id) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "item-" + id,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
                "conversation",
                "vec-" + id,
                "raw-" + id,
                "hash-" + id,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                null,
                MemoryItemType.FACT);
    }

    private record TestThreadSettings(
            boolean enabled,
            int maxThreads,
            int maxMembersPerThread,
            int protectDirectTopK,
            Duration timeout)
            implements RetrievalMemoryThreadSettings {}

    private static final class SlowThreadRanker extends ThreadAssistThreadRanker {

        private SlowThreadRanker(InMemoryMemoryStore store, Duration dormantAfter, Clock clock) {
            super(store, dormantAfter, clock);
        }

        @Override
        RankOutcome rank(
                QueryContext context,
                List<ScoredResult> seeds,
                RetrievalMemoryThreadSettings settings) {
            try {
                Thread.sleep(settings.timeout().toMillis() * 5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return super.rank(context, seeds, settings);
        }
    }

    private static final class ExplodingThreadRanker extends ThreadAssistThreadRanker {

        private ExplodingThreadRanker(
                InMemoryMemoryStore store, Duration dormantAfter, Clock clock) {
            super(store, dormantAfter, clock);
        }

        @Override
        RankOutcome rank(
                QueryContext context,
                List<ScoredResult> seeds,
                RetrievalMemoryThreadSettings settings) {
            throw new IllegalStateException("boom");
        }
    }
}
