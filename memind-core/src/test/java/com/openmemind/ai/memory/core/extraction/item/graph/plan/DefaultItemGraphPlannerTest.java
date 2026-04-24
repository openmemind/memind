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
package com.openmemind.ai.memory.core.extraction.item.graph.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.alias.EntityAliasIndexPlanner;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionStrategy;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.ExactCanonicalEntityResolutionStrategy;
import com.openmemind.ai.memory.core.extraction.item.graph.link.semantic.SemanticItemLinker;
import com.openmemind.ai.memory.core.extraction.item.graph.link.temporal.TemporalItemLinker;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.GraphHintNormalizer;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class DefaultItemGraphPlannerTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-04-19T00:00:00Z");

    @Test
    void plannerBuildsTypedWritePlanFromStructuredTemporalAndSemanticFamilies() {
        var planner =
                planner(
                        temporalLinkerReturning(beforeLink(101L, 102L)),
                        semanticLinkerReturning(semanticLink(101L, 201L)));

        StepVerifier.create(planner.plan(MEMORY_ID, batchItems(), batchEntries()))
                .assertNext(
                        result -> {
                            // Causal relations in this fixture come from GraphHintNormalizer over
                            // batchEntries(), not from the temporal or semantic linker stubs.
                            assertThat(result.writePlan().temporalRelations()).hasSize(1);
                            assertThat(result.writePlan().semanticRelations()).hasSize(1);
                            assertThat(result.writePlan().causalRelations()).hasSize(1);
                            assertThat(result.stats().structuredItemLinkCount()).isEqualTo(1);
                            assertThat(result.stats().temporalCreatedLinkCount()).isEqualTo(1);
                            assertThat(result.stats().temporalMinStrength()).isEqualTo(0.88d);
                            assertThat(result.stats().temporalMaxStrength()).isEqualTo(0.88d);
                            assertThat(result.stats().temporalStrengthBucketSummary())
                                    .isEqualTo("0.75-0.89=1");
                            assertThat(result.stats().semanticLinkCount()).isEqualTo(1);
                        })
                .verifyComplete();
    }

    @Test
    void plannerTurnsStageFailuresIntoFamilyScopedDegradationInsteadOfThrowing() {
        var planner = planner(temporalLinkerFailing(), semanticLinkerFailing());

        StepVerifier.create(planner.plan(MEMORY_ID, batchItems(), batchEntries()))
                .assertNext(
                        result -> {
                            assertThat(result.stats().temporalDegraded()).isTrue();
                            assertThat(result.stats().semanticDegraded()).isTrue();
                        })
                .verifyComplete();
    }

    @Test
    void
            plannerMarksStructuredBatchDegradedWhenStructuredResolutionThrowsButStillReturnsOtherFamilies() {
        var resolutionStrategy = mock(EntityResolutionStrategy.class);
        when(resolutionStrategy.resolve(eq(MEMORY_ID), any(), any()))
                .thenThrow(new IllegalStateException("structured stage failed"));
        var planner =
                planner(
                        new GraphHintNormalizer(),
                        resolutionStrategy,
                        temporalLinkerReturning(beforeLink(101L, 102L)),
                        semanticLinkerReturning(semanticLink(101L, 201L)));

        StepVerifier.create(planner.plan(MEMORY_ID, batchItems(), batchEntries()))
                .assertNext(
                        result -> {
                            assertThat(result.stats().structuredBatchDegraded()).isTrue();
                            assertThat(result.stats().temporalCreatedLinkCount()).isEqualTo(1);
                            assertThat(result.stats().semanticLinkCount()).isEqualTo(1);
                            assertThat(result.writePlan().causalRelations()).isEmpty();
                        })
                .verifyComplete();
    }

    @Test
    void plannerRunsTemporalAndSemanticBranchesConcurrently() {
        var bothEntered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var maxConcurrent = new AtomicInteger();
        var active = new AtomicInteger();
        var temporalLinker = mock(TemporalItemLinker.class);
        when(temporalLinker.plan(eq(MEMORY_ID), any()))
                .thenReturn(
                        blockingStage(
                                new TemporalItemLinker.TemporalLinkingPlan(
                                        List.of(beforeLink(101L, 102L)),
                                        TemporalItemLinker.TemporalLinkingStats.success(
                                                2,
                                                0,
                                                0,
                                                0,
                                                1,
                                                1,
                                                0L,
                                                0L,
                                                0L,
                                                0,
                                                0.88d,
                                                0.88d,
                                                "0.75-0.89=1",
                                                false)),
                                active,
                                maxConcurrent,
                                bothEntered,
                                release));
        var semanticLinker = mock(SemanticItemLinker.class);
        when(semanticLinker.plan(eq(MEMORY_ID), any()))
                .thenReturn(
                        blockingStage(
                                new SemanticItemLinker.SemanticLinkingPlan(
                                        List.of(semanticLink(101L, 201L)),
                                        new SemanticItemLinker.SemanticLinkingStats(
                                                0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L,
                                                0L, false)),
                                active,
                                maxConcurrent,
                                bothEntered,
                                release));
        var planner = planner(temporalLinker, semanticLinker);

        StepVerifier.create(planner.plan(MEMORY_ID, batchItems(), batchEntries()))
                .then(() -> assertThat(awaitLatch(bothEntered)).isTrue())
                .then(release::countDown)
                .assertNext(result -> assertThat(maxConcurrent.get()).isEqualTo(2))
                .verifyComplete();
    }

    private static DefaultItemGraphPlanner planner(
            TemporalItemLinker temporalLinker, SemanticItemLinker semanticLinker) {
        return planner(
                new GraphHintNormalizer(),
                new ExactCanonicalEntityResolutionStrategy(),
                temporalLinker,
                semanticLinker);
    }

    private static DefaultItemGraphPlanner planner(
            GraphHintNormalizer normalizer,
            EntityResolutionStrategy resolutionStrategy,
            TemporalItemLinker temporalLinker,
            SemanticItemLinker semanticLinker) {
        return new DefaultItemGraphPlanner(
                normalizer,
                resolutionStrategy,
                new EntityAliasIndexPlanner(),
                temporalLinker,
                semanticLinker,
                ItemGraphOptions.defaults().withEnabled(true));
    }

    private static DefaultItemGraphPlanner planner(
            GraphHintNormalizer normalizer,
            TemporalItemLinker temporalLinker,
            SemanticItemLinker semanticLinker) {
        return planner(
                normalizer,
                new ExactCanonicalEntityResolutionStrategy(),
                temporalLinker,
                semanticLinker);
    }

    private static TemporalItemLinker temporalLinkerReturning(ItemLink link) {
        var linker = mock(TemporalItemLinker.class);
        when(linker.plan(eq(MEMORY_ID), any()))
                .thenReturn(
                        Mono.just(
                                new TemporalItemLinker.TemporalLinkingPlan(
                                        List.of(link),
                                        TemporalItemLinker.TemporalLinkingStats.success(
                                                2,
                                                0,
                                                0,
                                                0,
                                                1,
                                                1,
                                                0L,
                                                0L,
                                                0L,
                                                0,
                                                0.88d,
                                                0.88d,
                                                "0.75-0.89=1",
                                                false))));
        return linker;
    }

    private static TemporalItemLinker temporalLinkerFailing() {
        var linker = mock(TemporalItemLinker.class);
        when(linker.plan(eq(MEMORY_ID), any()))
                .thenReturn(Mono.error(new IllegalStateException("temporal stage failed")));
        return linker;
    }

    private static SemanticItemLinker semanticLinkerReturning(ItemLink link) {
        var linker = mock(SemanticItemLinker.class);
        when(linker.plan(eq(MEMORY_ID), any()))
                .thenReturn(
                        Mono.just(
                                new SemanticItemLinker.SemanticLinkingPlan(
                                        List.of(link),
                                        new SemanticItemLinker.SemanticLinkingStats(
                                                0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L,
                                                0L, false))));
        return linker;
    }

    private static SemanticItemLinker semanticLinkerFailing() {
        var linker = mock(SemanticItemLinker.class);
        when(linker.plan(eq(MEMORY_ID), any()))
                .thenReturn(Mono.error(new IllegalStateException("semantic stage failed")));
        return linker;
    }

    private static <T> Mono<T> blockingStage(
            T value,
            AtomicInteger active,
            AtomicInteger maxConcurrent,
            CountDownLatch bothEntered,
            CountDownLatch release) {
        return Mono.fromCallable(
                        () -> {
                            int concurrent = active.incrementAndGet();
                            maxConcurrent.accumulateAndGet(concurrent, Math::max);
                            bothEntered.countDown();
                            assertThat(bothEntered.await(1, TimeUnit.SECONDS)).isTrue();
                            assertThat(release.await(1, TimeUnit.SECONDS)).isTrue();
                            active.decrementAndGet();
                            return value;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static boolean awaitLatch(CountDownLatch latch) {
        try {
            return latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("unexpected interruption while waiting for latch", error);
        }
    }

    private static List<MemoryItem> batchItems() {
        return List.of(item(101L, "Cause item"), item(102L, "Effect item"));
    }

    private static List<ExtractedMemoryEntry> batchEntries() {
        return List.of(
                entry("Cause item", List.of(), List.of()),
                entry(
                        "Effect item",
                        List.of(
                                new ExtractedGraphHints.ExtractedEntityHint(
                                        "OpenAI", "organization", 0.95f)),
                        List.of(
                                new ExtractedGraphHints.ExtractedCausalRelationHint(
                                        0, 1, "caused_by", 0.92f))));
    }

    private static MemoryItem item(Long id, String content) {
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
                NOW.plusSeconds(id),
                NOW,
                Map.of(),
                NOW,
                MemoryItemType.FACT);
    }

    private static ExtractedMemoryEntry entry(
            String content,
            List<ExtractedGraphHints.ExtractedEntityHint> entityHints,
            List<ExtractedGraphHints.ExtractedCausalRelationHint> causalHints) {
        return new ExtractedMemoryEntry(
                content,
                1.0f,
                null,
                null,
                null,
                null,
                NOW,
                "raw-" + Math.abs(content.hashCode()),
                null,
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event",
                new ExtractedGraphHints(entityHints, causalHints));
    }

    private static ItemLink beforeLink(long sourceItemId, long targetItemId) {
        return new ItemLink(
                MEMORY_ID.toIdentifier(),
                sourceItemId,
                targetItemId,
                ItemLinkType.TEMPORAL,
                "before",
                null,
                0.88d,
                Map.of(),
                NOW);
    }

    private static ItemLink semanticLink(long sourceItemId, long targetItemId) {
        return new ItemLink(
                MEMORY_ID.toIdentifier(),
                sourceItemId,
                targetItemId,
                ItemLinkType.SEMANTIC,
                null,
                "vector_search",
                0.81d,
                Map.of(),
                NOW);
    }
}
