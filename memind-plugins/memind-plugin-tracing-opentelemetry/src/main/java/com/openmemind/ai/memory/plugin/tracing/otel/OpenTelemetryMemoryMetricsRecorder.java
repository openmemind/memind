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
package com.openmemind.ai.memory.plugin.tracing.otel;

import com.openmemind.ai.memory.core.metrics.ExtractionMetrics;
import com.openmemind.ai.memory.core.metrics.MemoryMetricsRecorder;
import com.openmemind.ai.memory.core.metrics.RetrievalMergeMetrics;
import com.openmemind.ai.memory.core.metrics.RetrievalResultType;
import com.openmemind.ai.memory.core.metrics.RetrievalStageMetrics;
import com.openmemind.ai.memory.core.metrics.RetrievalSummaryMetrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.Set;

public final class OpenTelemetryMemoryMetricsRecorder implements MemoryMetricsRecorder {

    private static final Set<String> STRATEGIES = Set.of("simple", "deep", "unknown");
    private static final Set<String> STAGES = Set.of("tier", "channel", "merge", "rerank", "final");
    private static final Set<String> TIERS = Set.of("item", "insight", "raw_data", "none");
    private static final Set<String> METHODS =
            Set.of(
                    "vector",
                    "keyword",
                    "hybrid",
                    "graph",
                    "temporal",
                    "rrf",
                    "rerank",
                    "final",
                    "none");
    private static final Set<String> STATUSES =
            Set.of(
                    "success",
                    "error",
                    "degraded",
                    "skipped",
                    "empty",
                    "failed",
                    "partial_success",
                    "unknown");
    private static final Set<String> SOURCES = Set.of("api", "core", "internal");

    private final DoubleHistogram extractionRawData;
    private final DoubleHistogram extractionSegments;
    private final DoubleHistogram extractionItems;
    private final DoubleHistogram extractionItemsNew;
    private final DoubleHistogram extractionItemsReinforced;
    private final DoubleHistogram extractionInsights;
    private final DoubleHistogram extractionGraphEntities;
    private final DoubleHistogram extractionGraphMentions;
    private final DoubleHistogram extractionGraphRelations;
    private final DoubleHistogram retrievalCandidates;
    private final DoubleHistogram retrievalResults;
    private final DoubleHistogram retrievalMergeInputs;
    private final DoubleHistogram retrievalMergeOutputs;
    private final DoubleHistogram retrievalMergeDeduplicated;
    private final LongCounter retrievalStageDegraded;
    private final LongCounter retrievalStageSkipped;
    private final LongCounter retrievalEmptyResults;

    public OpenTelemetryMemoryMetricsRecorder(Meter meter) {
        this.extractionRawData = histogram(meter, "memind.extraction.raw_data", "{raw_data}");
        this.extractionSegments = histogram(meter, "memind.extraction.segments", "{segment}");
        this.extractionItems = histogram(meter, "memind.extraction.items", "{item}");
        this.extractionItemsNew = histogram(meter, "memind.extraction.items.new", "{item}");
        this.extractionItemsReinforced =
                histogram(meter, "memind.extraction.items.reinforced", "{item}");
        this.extractionInsights = histogram(meter, "memind.extraction.insights", "{insight}");
        this.extractionGraphEntities =
                histogram(meter, "memind.extraction.graph.entities", "{entity}");
        this.extractionGraphMentions =
                histogram(meter, "memind.extraction.graph.mentions", "{mention}");
        this.extractionGraphRelations =
                histogram(meter, "memind.extraction.graph.relations", "{relation}");
        this.retrievalCandidates = histogram(meter, "memind.retrieval.candidates", "{candidate}");
        this.retrievalResults = histogram(meter, "memind.retrieval.results", "{result}");
        this.retrievalMergeInputs =
                histogram(meter, "memind.retrieval.merge.inputs", "{candidate}");
        this.retrievalMergeOutputs =
                histogram(meter, "memind.retrieval.merge.outputs", "{candidate}");
        this.retrievalMergeDeduplicated =
                histogram(meter, "memind.retrieval.merge.deduplicated", "{candidate}");
        this.retrievalStageDegraded = counter(meter, "memind.retrieval.stage.degraded");
        this.retrievalStageSkipped = counter(meter, "memind.retrieval.stage.skipped");
        this.retrievalEmptyResults = counter(meter, "memind.retrieval.empty_results");
    }

    @Override
    public void recordExtractionSummary(ExtractionMetrics metrics) {
        if (metrics == null) {
            return;
        }
        Attributes attrs = extractionAttrs(metrics.status(), metrics.source());
        extractionRawData.record(metrics.rawDataCount(), attrs);
        recordNullable(extractionSegments, metrics.segmentCount(), attrs);
        extractionItems.record(metrics.newItemCount(), attrs);
        extractionItemsNew.record(metrics.newItemCount(), attrs);
        recordNullable(extractionItemsReinforced, metrics.reinforcedItemCount(), attrs);
        extractionInsights.record(metrics.insightCount(), attrs);
        recordNullable(extractionGraphEntities, metrics.graphEntityCount(), attrs);
        recordNullable(extractionGraphMentions, metrics.graphMentionCount(), attrs);
        recordNullable(extractionGraphRelations, metrics.graphRelationCount(), attrs);
    }

    @Override
    public void recordRetrievalStage(RetrievalStageMetrics metrics) {
        if (metrics == null) {
            return;
        }
        Attributes attrs =
                retrievalAttrs(
                        metrics.strategy(),
                        metrics.stage(),
                        metrics.tier(),
                        metrics.method(),
                        metrics.status(),
                        metrics.source());
        recordNullable(retrievalCandidates, metrics.candidateCount(), attrs);
        recordNullable(retrievalResults, metrics.resultCount(), attrs);
        if (metrics.degraded()) {
            retrievalStageDegraded.add(1, attrs);
        }
        if (metrics.skipped()) {
            retrievalStageSkipped.add(1, attrs);
        }
    }

    @Override
    public void recordRetrievalMerge(RetrievalMergeMetrics metrics) {
        if (metrics == null) {
            return;
        }
        Attributes attrs =
                retrievalAttrs(
                        metrics.strategy(),
                        "merge",
                        "none",
                        "rrf",
                        metrics.status(),
                        metrics.source());
        retrievalMergeInputs.record(metrics.inputCount(), attrs);
        retrievalMergeOutputs.record(metrics.outputCount(), attrs);
        retrievalMergeDeduplicated.record(metrics.deduplicatedCount(), attrs);
    }

    @Override
    public void recordRetrievalSummary(RetrievalSummaryMetrics metrics) {
        if (metrics == null) {
            return;
        }
        Attributes attrs =
                retrievalAttrs(
                        metrics.strategy(),
                        "final",
                        "none",
                        "final",
                        metrics.status(),
                        metrics.source());
        recordFinalResult(metrics, RetrievalResultType.ITEM, attrs);
        recordFinalResult(metrics, RetrievalResultType.INSIGHT, attrs);
        recordFinalResult(metrics, RetrievalResultType.RAW_DATA, attrs);
        recordFinalResult(metrics, RetrievalResultType.EVIDENCE, attrs);
        if (metrics.itemCount() == 0
                && metrics.insightCount() == 0
                && metrics.rawDataCount() == 0) {
            retrievalEmptyResults.add(1, attrs);
        }
    }

    private void recordFinalResult(
            RetrievalSummaryMetrics metrics, RetrievalResultType type, Attributes baseAttrs) {
        retrievalResults.record(
                metrics.countFor(type),
                baseAttrs.toBuilder()
                        .put(AttributeKey.stringKey("result_type"), resultType(type))
                        .build());
    }

    private static DoubleHistogram histogram(Meter meter, String name, String unit) {
        return meter.histogramBuilder(name).setUnit(unit).build();
    }

    private static LongCounter counter(Meter meter, String name) {
        return meter.counterBuilder(name).setUnit("{event}").build();
    }

    private static void recordNullable(DoubleHistogram histogram, Integer value, Attributes attrs) {
        if (value != null) {
            histogram.record(value, attrs);
        }
    }

    private static Attributes extractionAttrs(String status, String source) {
        return Attributes.of(
                AttributeKey.stringKey("operation"),
                "extraction",
                AttributeKey.stringKey("status"),
                normalize(status, STATUSES, "unknown"),
                AttributeKey.stringKey("source"),
                normalize(source, SOURCES, "core"));
    }

    private static Attributes retrievalAttrs(
            String strategy,
            String stage,
            String tier,
            String method,
            String status,
            String source) {
        return Attributes.builder()
                .put(AttributeKey.stringKey("operation"), "retrieval")
                .put(AttributeKey.stringKey("strategy"), normalize(strategy, STRATEGIES, "unknown"))
                .put(AttributeKey.stringKey("stage"), normalize(stage, STAGES, "final"))
                .put(AttributeKey.stringKey("tier"), normalize(tier, TIERS, "none"))
                .put(AttributeKey.stringKey("method"), normalize(method, METHODS, "none"))
                .put(AttributeKey.stringKey("status"), normalize(status, STATUSES, "unknown"))
                .put(AttributeKey.stringKey("source"), normalize(source, SOURCES, "core"))
                .build();
    }

    private static String resultType(RetrievalResultType type) {
        return switch (type) {
            case ITEM -> "item";
            case INSIGHT -> "insight";
            case RAW_DATA -> "raw_data";
            case EVIDENCE -> "evidence";
        };
    }

    private static String normalize(String value, Set<String> allowed, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.toLowerCase();
        return allowed.contains(normalized) ? normalized : fallback;
    }
}
