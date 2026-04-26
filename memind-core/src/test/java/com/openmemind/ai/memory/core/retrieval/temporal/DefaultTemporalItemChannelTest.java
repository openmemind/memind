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
package com.openmemind.ai.memory.core.retrieval.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InMemoryInsightOperations;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.InMemoryRawDataOperations;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultTemporalItemChannelTest {

    private static final DefaultMemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final RetrievalConfig CONFIG = RetrievalConfig.simple();
    private static final TemporalItemChannelSettings SETTINGS =
            TemporalItemChannelSettings.defaults();

    @Test
    void noConstraintReturnsEmptyResult() {
        var channel = new DefaultTemporalItemChannel(store(new InMemoryItemOperations()));
        var result =
                channel.retrieve(context(null, null), CONFIG, Optional.empty(), SETTINGS).block();

        assertThat(result).isNotNull();
        assertThat(result.items()).isEmpty();
        assertThat(result.constraintPresent()).isFalse();
    }

    @Test
    void futureQueryReturnsFutureSemanticOccurrence() {
        var operations = new InMemoryItemOperations();
        operations.insertItems(
                MEMORY_ID,
                List.of(
                        item(
                                1,
                                "明天去爬山",
                                null,
                                Instant.parse("2026-04-27T00:00:00Z"),
                                Instant.parse("2026-04-28T00:00:00Z"),
                                null,
                                MemoryCategory.EVENT),
                        item(
                                2,
                                "只是明天观察到的记录",
                                null,
                                null,
                                null,
                                Instant.parse("2026-04-27T10:00:00Z"),
                                MemoryCategory.EVENT)));
        var channel = new DefaultTemporalItemChannel(store(operations));

        var result =
                channel.retrieve(
                                context(null, Set.of(MemoryCategory.EVENT)),
                                CONFIG,
                                Optional.of(futureDayConstraint()),
                                SETTINGS)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.items()).extracting(ScoredResult::sourceId).containsExactly("1");
        assertThat(result.items().getFirst().vectorScore()).isZero();
    }

    @Test
    void scoresByTemporalProximityOnlyAndRespectsTopK() {
        var operations = new InMemoryItemOperations();
        operations.insertItems(
                MEMORY_ID,
                List.of(
                        item(
                                1,
                                "midday",
                                Instant.parse("2026-04-27T12:00:00Z"),
                                null,
                                null,
                                null,
                                MemoryCategory.EVENT),
                        item(
                                2,
                                "morning",
                                Instant.parse("2026-04-27T08:00:00Z"),
                                null,
                                null,
                                null,
                                MemoryCategory.EVENT),
                        item(
                                3,
                                "late",
                                Instant.parse("2026-04-27T20:00:00Z"),
                                null,
                                null,
                                null,
                                MemoryCategory.EVENT)));
        var channel = new DefaultTemporalItemChannel(store(operations));
        var config = CONFIG.withTier2(RetrievalConfig.TierConfig.enabled(2, 0.1));

        var result =
                channel.retrieve(
                                context(null, Set.of(MemoryCategory.EVENT)),
                                config,
                                Optional.of(futureDayConstraint()),
                                SETTINGS)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.items()).extracting(ScoredResult::sourceId).containsExactly("1", "2");
        assertThat(result.items().get(0).finalScore()).isEqualTo(1.0d);
        assertThat(result.items().get(1).vectorScore()).isZero();
    }

    @Test
    void appliesRetrievalGuardScopeAndCategoryFiltering() {
        var operations = new InMemoryItemOperations();
        operations.insertItems(
                MEMORY_ID,
                List.of(
                        item(
                                1,
                                "event",
                                Instant.parse("2026-04-27T12:00:00Z"),
                                null,
                                null,
                                null,
                                MemoryCategory.EVENT),
                        item(
                                2,
                                "profile",
                                Instant.parse("2026-04-27T12:00:00Z"),
                                null,
                                null,
                                null,
                                MemoryCategory.PROFILE)));
        var channel = new DefaultTemporalItemChannel(store(operations));

        var result =
                channel.retrieve(
                                context(MemoryScope.USER, Set.of(MemoryCategory.EVENT)),
                                CONFIG,
                                Optional.of(futureDayConstraint()),
                                SETTINGS)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.items()).extracting(ScoredResult::sourceId).containsExactly("1");
    }

    private static QueryContext context(MemoryScope scope, Set<MemoryCategory> categories) {
        return new QueryContext(MEMORY_ID, "明天计划", null, List.of(), Map.of(), scope, categories);
    }

    private static TemporalConstraint futureDayConstraint() {
        return new TemporalConstraint(
                Instant.parse("2026-04-27T00:00:00Z"),
                Instant.parse("2026-04-28T00:00:00Z"),
                Instant.parse("2026-04-26T10:00:00Z"),
                TemporalGranularity.DAY,
                TemporalDirection.FUTURE,
                TemporalConstraintSource.RELATIVE_DAY,
                1.0d);
    }

    private static MemoryStore store(InMemoryItemOperations itemOperations) {
        return MemoryStore.of(
                new InMemoryRawDataOperations(), itemOperations, new InMemoryInsightOperations());
    }

    private static MemoryItem item(
            long id,
            String content,
            Instant occurredAt,
            Instant occurredStart,
            Instant occurredEnd,
            Instant observedAt,
            MemoryCategory category) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                content,
                MemoryScope.USER,
                category,
                "conversation",
                String.valueOf(id),
                "raw-" + id,
                "hash-" + id,
                occurredAt,
                occurredStart,
                occurredEnd,
                "DAY",
                observedAt,
                Map.of(),
                Instant.parse("2026-04-20T00:00:00Z"),
                MemoryItemType.FACT);
    }
}
