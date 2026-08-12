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
package com.openmemind.ai.memory.plugin.observability.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.observation.DefaultMemoryExtractorObservation.ExtractionObservationContext;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.observation.DefaultMemoryRetrieverObservation.RetrievalObservationContext;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult.SourceType;
import com.openmemind.ai.memory.core.retrieval.scoring.observation.DefaultRetrievalResultMergerObservation.ResultMergeObservationContext;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannelResult;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannelSettings;
import com.openmemind.ai.memory.core.retrieval.temporal.observation.DefaultTemporalItemChannelObservation.TemporalItemChannelObservationContext;
import com.openmemind.ai.memory.core.retrieval.tier.observation.ItemTierRetrieverObservation.ItemTierDocument;
import com.openmemind.ai.memory.core.retrieval.tier.observation.ItemTierRetrieverObservation.ItemTierObservationContext;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MemoryMeterObservationHandlerTest {

    @Test
    void recordsBusinessMetricPayloadsFromObservationContexts() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry
                .observationConfig()
                .observationHandler(new MemoryMeterObservationHandler(meterRegistry));

        ExtractionObservationContext extraction = new ExtractionObservationContext(memoryId());
        observe(
                observationRegistry,
                extraction,
                () -> extraction.recordResult(extractionResult(1, 2, 3)));

        ItemTierObservationContext tier =
                new ItemTierObservationContext(
                        queryContext(), "item", ItemTierDocument.VECTOR_SEARCH, 5);
        observe(observationRegistry, tier, () -> tier.recordResults(scoredResults(5)));

        TemporalItemChannelObservationContext temporal =
                new TemporalItemChannelObservationContext(
                        queryContext(), Optional.empty(), TemporalItemChannelSettings.defaults());
        observe(
                observationRegistry,
                temporal,
                () ->
                        temporal.recordResult(
                                new TemporalItemChannelResult(
                                        scoredResults(2), true, true, true, 3)));

        ResultMergeObservationContext merge =
                new ResultMergeObservationContext(
                        List.of(scoredResults(10), scoredResults(10, 10)), new double[] {1.0, 1.0});
        observe(observationRegistry, merge, () -> merge.recordResult(scoredResults(12)));

        RetrievalObservationContext summary = new RetrievalObservationContext(memoryId());
        observe(
                observationRegistry,
                summary,
                () -> summary.recordResult(RetrievalResult.empty("simple", "query")));

        RetrievalObservationContext deepSummary = new RetrievalObservationContext(memoryId());
        observe(
                observationRegistry,
                deepSummary,
                () -> deepSummary.recordResult(RetrievalResult.empty("deep_retrieval", "query")));

        RetrievalObservationContext degradedSummary = new RetrievalObservationContext(memoryId());
        observe(
                observationRegistry,
                degradedSummary,
                () -> degradedSummary.recordResult(RetrievalResult.degraded("simple", "query")));

        Tags extractionTags =
                Tags.of("operation", "extraction", "status", "success", "source", "core");
        assertThat(summaryTotal(meterRegistry, "memind.extraction.raw_data", extractionTags))
                .isEqualTo(1.0);
        assertThat(meterRegistry.find("memind.extraction.segments").tags(extractionTags).summary())
                .isNull();
        assertThat(summaryTotal(meterRegistry, "memind.extraction.items", extractionTags))
                .isEqualTo(2.0);
        assertThat(summaryTotal(meterRegistry, "memind.extraction.insights", extractionTags))
                .isEqualTo(3.0);

        Tags tierTags =
                Tags.of(
                        "operation", "retrieval",
                        "strategy", "unknown",
                        "stage", "tier",
                        "tier", "item",
                        "method", "vector",
                        "status", "success",
                        "source", "core");
        assertThat(
                        summaryTotal(
                                meterRegistry,
                                "memind.retrieval.results",
                                tierTags.and("result_type", "none")))
                .isEqualTo(5.0);

        Tags degradedTags =
                Tags.of(
                        "operation", "retrieval",
                        "strategy", "unknown",
                        "stage", "channel",
                        "tier", "item",
                        "method", "temporal",
                        "status", "degraded",
                        "source", "core");
        assertThat(summaryTotal(meterRegistry, "memind.retrieval.candidates", degradedTags))
                .isEqualTo(3.0);
        assertThat(
                        meterRegistry
                                .find("memind.retrieval.stage.degraded")
                                .tags(degradedTags)
                                .counter()
                                .count())
                .isEqualTo(1.0);

        Tags mergeTags =
                Tags.of(
                        "operation", "retrieval",
                        "strategy", "unknown",
                        "stage", "merge",
                        "tier", "none",
                        "method", "rrf",
                        "status", "success",
                        "source", "core");
        assertThat(summaryTotal(meterRegistry, "memind.retrieval.merge.inputs", mergeTags))
                .isEqualTo(20.0);
        assertThat(summaryTotal(meterRegistry, "memind.retrieval.merge.outputs", mergeTags))
                .isEqualTo(12.0);
        assertThat(summaryTotal(meterRegistry, "memind.retrieval.merge.deduplicated", mergeTags))
                .isEqualTo(8.0);

        Tags finalTags =
                Tags.of(
                        "operation", "retrieval",
                        "strategy", "simple",
                        "stage", "final",
                        "tier", "none",
                        "method", "final",
                        "status", "empty",
                        "source", "core");
        assertThat(
                        meterRegistry
                                .find("memind.retrieval.empty_results")
                                .tags(finalTags)
                                .counter()
                                .count())
                .isEqualTo(1.0);
        assertThat(
                        meterRegistry
                                .find("memind.retrieval.empty_results")
                                .tags(
                                        Tags.of(
                                                "operation", "retrieval",
                                                "strategy", "simple",
                                                "stage", "final",
                                                "tier", "none",
                                                "method", "final",
                                                "status", "degraded",
                                                "source", "core"))
                                .counter())
                .isNull();
        assertThat(
                        summaryTotal(
                                meterRegistry,
                                "memind.retrieval.results",
                                finalTags.and("result_type", "item")))
                .isEqualTo(0.0);

        Tags deepFinalTags =
                Tags.of(
                        "operation", "retrieval",
                        "strategy", "deep_retrieval",
                        "stage", "final",
                        "tier", "none",
                        "method", "final",
                        "status", "empty",
                        "source", "core");
        assertThat(
                        meterRegistry
                                .find("memind.retrieval.empty_results")
                                .tags(deepFinalTags)
                                .counter()
                                .count())
                .isEqualTo(1.0);
        assertThat(resultTagKeySets(meterRegistry))
                .containsExactly(
                        Set.of(
                                "operation",
                                "strategy",
                                "stage",
                                "tier",
                                "method",
                                "status",
                                "source",
                                "result_type"));
    }

    private static void observe(
            ObservationRegistry registry, Observation.Context context, Runnable operation) {
        Observation.createNotStarted("memind.test", () -> context, registry).observe(operation);
    }

    private static ExtractionResult extractionResult(
            int rawDataCount, int itemCount, int insightCount) {
        return ExtractionResult.success(
                memoryId(),
                new RawDataResult(rawData(rawDataCount), null, false),
                new MemoryItemResult(memoryItems(itemCount), List.of()),
                new InsightResult(insights(insightCount)),
                Duration.ZERO);
    }

    private static MemoryId memoryId() {
        return () -> "memory-1";
    }

    private static QueryContext queryContext() {
        return new QueryContext(memoryId(), "query", null, List.of(), Map.of(), null, Set.of());
    }

    private static List<ScoredResult> scoredResults(int count) {
        return scoredResults(count, 0);
    }

    private static List<ScoredResult> scoredResults(int count, int offset) {
        return IntStream.range(0, count)
                .mapToObj(
                        index ->
                                new ScoredResult(
                                        SourceType.ITEM,
                                        String.valueOf(offset + index),
                                        "text",
                                        1.0f,
                                        1.0d))
                .toList();
    }

    private static List<MemoryRawData> rawData(int count) {
        return IntStream.range(0, count)
                .mapToObj(
                        index ->
                                new MemoryRawData(
                                        String.valueOf(index),
                                        "memory-1",
                                        "conversation",
                                        null,
                                        null,
                                        null,
                                        null,
                                        Map.of(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null))
                .toList();
    }

    private static List<MemoryItem> memoryItems(int count) {
        return IntStream.range(0, count)
                .mapToObj(
                        index ->
                                new MemoryItem(
                                        (long) index,
                                        "memory-1",
                                        "content",
                                        null,
                                        null,
                                        "conversation",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        Map.of(),
                                        null,
                                        null))
                .toList();
    }

    private static List<MemoryInsight> insights(int count) {
        return IntStream.range(0, count)
                .mapToObj(
                        index ->
                                new MemoryInsight(
                                        (long) index,
                                        "memory-1",
                                        "type",
                                        null,
                                        "name",
                                        List.of(),
                                        List.of(),
                                        null,
                                        null,
                                        List.of(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        List.of(),
                                        1))
                .toList();
    }

    private static double summaryTotal(
            SimpleMeterRegistry registry,
            String name,
            Iterable<io.micrometer.core.instrument.Tag> tags) {
        return registry.find(name).tags(tags).summary().totalAmount();
    }

    private static Set<Set<String>> resultTagKeySets(SimpleMeterRegistry registry) {
        return registry.find("memind.retrieval.results").meters().stream()
                .map(
                        meter ->
                                meter.getId().getTags().stream()
                                        .map(io.micrometer.core.instrument.Tag::getKey)
                                        .collect(Collectors.toSet()))
                .collect(Collectors.toSet());
    }
}
