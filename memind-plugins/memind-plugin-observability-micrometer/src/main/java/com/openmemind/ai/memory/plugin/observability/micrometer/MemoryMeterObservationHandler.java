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

import com.openmemind.ai.memory.core.extraction.observation.DefaultMemoryExtractorObservation.ExtractionObservationContext;
import com.openmemind.ai.memory.core.llm.rerank.observation.LlmRerankerObservation.RerankObservationContext;
import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import com.openmemind.ai.memory.core.retrieval.deep.observation.LlmTypedQueryExpanderObservation.MultiQueryExpandObservationContext;
import com.openmemind.ai.memory.core.retrieval.graph.observation.DefaultGraphItemChannelObservation.GraphItemChannelObservationContext;
import com.openmemind.ai.memory.core.retrieval.graph.observation.DefaultRetrievalGraphAssistantObservation.GraphAssistObservationContext;
import com.openmemind.ai.memory.core.retrieval.scoring.observation.DefaultRetrievalResultMergerObservation.ResultMergeObservationContext;
import com.openmemind.ai.memory.core.retrieval.strategy.observation.DeepRetrievalStrategyObservation;
import com.openmemind.ai.memory.core.retrieval.strategy.observation.SimpleRetrievalStrategyObservation;
import com.openmemind.ai.memory.core.retrieval.sufficiency.observation.LlmSufficiencyGateObservation.SufficiencyObservationContext;
import com.openmemind.ai.memory.core.retrieval.temporal.observation.DefaultTemporalItemChannelObservation.TemporalItemChannelObservationContext;
import com.openmemind.ai.memory.core.retrieval.tier.observation.InsightTierRetrieverObservation.InsightTierObservationContext;
import com.openmemind.ai.memory.core.retrieval.tier.observation.ItemTierRetrieverObservation.ItemTierObservationContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class MemoryMeterObservationHandler
        implements ObservationHandler<MemoryObservationContext> {

    private static final Set<String> STRATEGIES =
            Set.of("simple", "deep", "deep_retrieval", "unknown");
    private static final Set<String> STAGES =
            Set.of(
                    "tier",
                    "channel",
                    "merge",
                    "rerank",
                    "final",
                    "sufficiency",
                    "query_expand",
                    "graph_assist");
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
                    "llm",
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

    private final MeterRegistry meterRegistry;

    public MemoryMeterObservationHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @Override
    public void onStop(MemoryObservationContext context) {
        try {
            write(context);
        } catch (RuntimeException ignored) {
            // Metrics collection must never affect memory operations.
        }
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof MemoryObservationContext;
    }

    private void write(MemoryObservationContext context) {
        if (context instanceof ExtractionObservationContext extraction) {
            writeExtractionSummary(extraction);
        } else if (context instanceof ItemTierObservationContext itemTier) {
            writeRetrievalStage(
                    itemTier.strategyName(),
                    itemTier.stage(),
                    itemTier.tier(),
                    itemTier.method(),
                    itemTier.status(),
                    null,
                    itemTier.resultCount(),
                    false,
                    false,
                    itemTier.source());
        } else if (context instanceof InsightTierObservationContext insightTier) {
            writeRetrievalStage(
                    insightTier.strategyName(),
                    insightTier.stage(),
                    insightTier.tier(),
                    insightTier.method(),
                    insightTier.status(),
                    null,
                    insightTier.resultCount(),
                    false,
                    false,
                    insightTier.source());
        } else if (context instanceof GraphItemChannelObservationContext graphChannel) {
            writeRetrievalStage(
                    graphChannel.strategyName(),
                    graphChannel.stage(),
                    graphChannel.tier(),
                    graphChannel.method(),
                    graphChannel.status(),
                    graphChannel.candidateCount(),
                    graphChannel.resultCount(),
                    graphChannel.degraded(),
                    graphChannel.skipped(),
                    graphChannel.source());
        } else if (context instanceof TemporalItemChannelObservationContext temporalChannel) {
            writeRetrievalStage(
                    temporalChannel.strategyName(),
                    temporalChannel.stage(),
                    temporalChannel.tier(),
                    temporalChannel.method(),
                    temporalChannel.status(),
                    temporalChannel.candidateCount(),
                    temporalChannel.resultCount(),
                    temporalChannel.degraded(),
                    temporalChannel.skipped(),
                    temporalChannel.source());
        } else if (context instanceof MultiQueryExpandObservationContext queryExpand) {
            writeRetrievalStage(
                    null,
                    queryExpand.stage(),
                    queryExpand.tier(),
                    queryExpand.method(),
                    queryExpand.status(),
                    queryExpand.candidateCount(),
                    queryExpand.resultCount(),
                    queryExpand.degraded(),
                    queryExpand.skipped(),
                    queryExpand.source());
        } else if (context instanceof SufficiencyObservationContext sufficiency) {
            writeRetrievalStage(
                    null,
                    sufficiency.stage(),
                    sufficiency.tier(),
                    sufficiency.method(),
                    sufficiency.status(),
                    sufficiency.candidateCount(),
                    sufficiency.resultCount(),
                    sufficiency.degraded(),
                    sufficiency.skipped(),
                    sufficiency.source());
        } else if (context instanceof RerankObservationContext rerank) {
            writeRetrievalStage(
                    null,
                    rerank.stage(),
                    rerank.tier(),
                    rerank.method(),
                    rerank.status(),
                    rerank.candidateCount(),
                    rerank.resultCount(),
                    rerank.degraded(),
                    rerank.skipped(),
                    rerank.source());
        } else if (context instanceof GraphAssistObservationContext graphAssist) {
            writeRetrievalStage(
                    null,
                    graphAssist.stage(),
                    graphAssist.tier(),
                    graphAssist.method(),
                    graphAssist.status(),
                    graphAssist.candidateCount(),
                    graphAssist.resultCount(),
                    graphAssist.degraded(),
                    graphAssist.skipped(),
                    graphAssist.source());
        } else if (context instanceof ResultMergeObservationContext merge) {
            writeRetrievalMerge(merge);
        } else if (context
                instanceof SimpleRetrievalStrategyObservation.StrategyObservationContext simple) {
            writeRetrievalSummary(
                    simple.strategyName(),
                    simple.status(),
                    simple.itemCount(),
                    simple.insightCount(),
                    simple.rawDataCount(),
                    simple.evidenceCount(),
                    simple.source());
        } else if (context
                instanceof DeepRetrievalStrategyObservation.StrategyObservationContext deep) {
            writeRetrievalSummary(
                    deep.strategyName(),
                    deep.status(),
                    deep.itemCount(),
                    deep.insightCount(),
                    deep.rawDataCount(),
                    deep.evidenceCount(),
                    deep.source());
        }
    }

    private void writeExtractionSummary(ExtractionObservationContext metrics) {
        if (!metrics.hasResult() && metrics.getError() == null) {
            return;
        }
        Tags tags = extractionTags(metrics.status(), metrics.source());
        writeDistribution("memind.extraction.raw_data", "raw_data", metrics.rawDataCount(), tags);
        writeNullableDistribution(
                "memind.extraction.segments", "segment", metrics.segmentCount(), tags);
        writeDistribution("memind.extraction.items", "item", metrics.newItemCount(), tags);
        writeDistribution("memind.extraction.items.new", "item", metrics.newItemCount(), tags);
        writeNullableDistribution(
                "memind.extraction.items.reinforced", "item", metrics.reinforcedItemCount(), tags);
        writeDistribution("memind.extraction.insights", "insight", metrics.insightCount(), tags);
        writeNullableDistribution(
                "memind.extraction.graph.entities", "entity", metrics.graphEntityCount(), tags);
        writeNullableDistribution(
                "memind.extraction.graph.mentions", "mention", metrics.graphMentionCount(), tags);
        writeNullableDistribution(
                "memind.extraction.graph.relations",
                "relation",
                metrics.graphRelationCount(),
                tags);
    }

    private void writeRetrievalStage(
            String strategy,
            String stage,
            String tier,
            String method,
            String status,
            Integer candidateCount,
            Integer resultCount,
            boolean degraded,
            boolean skipped,
            String source) {
        Tags tags = retrievalTags(strategy, stage, tier, method, status, source);
        writeNullableDistribution("memind.retrieval.candidates", "candidate", candidateCount, tags);
        writeNullableDistribution(
                "memind.retrieval.results", "result", resultCount, tags.and("result_type", "none"));
        if (degraded) {
            incrementCounter("memind.retrieval.stage.degraded", tags);
        }
        if (skipped) {
            incrementCounter("memind.retrieval.stage.skipped", tags);
        }
    }

    private void writeRetrievalMerge(ResultMergeObservationContext metrics) {
        Tags tags =
                retrievalTags(
                        metrics.strategyName(),
                        "merge",
                        "none",
                        "rrf",
                        metrics.status(),
                        metrics.source());
        writeDistribution("memind.retrieval.merge.inputs", "candidate", metrics.inputCount(), tags);
        writeDistribution(
                "memind.retrieval.merge.outputs", "candidate", metrics.outputCount(), tags);
        writeDistribution(
                "memind.retrieval.merge.deduplicated",
                "candidate",
                metrics.deduplicatedCount(),
                tags);
    }

    private void writeRetrievalSummary(
            String strategy,
            String status,
            int itemCount,
            int insightCount,
            int rawDataCount,
            int evidenceCount,
            String source) {
        Tags tags = retrievalTags(strategy, "final", "none", "final", status, source);
        writeFinalResult(itemCount, "item", tags);
        writeFinalResult(insightCount, "insight", tags);
        writeFinalResult(rawDataCount, "raw_data", tags);
        writeFinalResult(evidenceCount, "evidence", tags);
        if (itemCount == 0 && insightCount == 0 && rawDataCount == 0) {
            incrementCounter("memind.retrieval.empty_results", tags);
        }
    }

    private void writeFinalResult(int count, String resultType, Tags baseTags) {
        writeDistribution(
                "memind.retrieval.results",
                "result",
                count,
                baseTags.and("result_type", resultType));
    }

    private void writeDistribution(String name, String baseUnit, double value, Tags tags) {
        DistributionSummary.builder(name)
                .baseUnit(baseUnit)
                .tags(tags)
                .register(meterRegistry)
                .record(value);
    }

    private void incrementCounter(String name, Tags tags) {
        Counter.builder(name).baseUnit("event").tags(tags).register(meterRegistry).increment();
    }

    private void writeNullableDistribution(String name, String baseUnit, Integer value, Tags tags) {
        if (value != null) {
            writeDistribution(name, baseUnit, value, tags);
        }
    }

    private static Tags extractionTags(String status, String source) {
        return Tags.of(
                "operation",
                "extraction",
                "status",
                normalize(status, STATUSES, "unknown"),
                "source",
                normalize(source, SOURCES, "core"));
    }

    private static Tags retrievalTags(
            String strategy,
            String stage,
            String tier,
            String method,
            String status,
            String source) {
        return Tags.of(
                "operation",
                "retrieval",
                "strategy",
                normalize(strategy, STRATEGIES, "unknown"),
                "stage",
                normalize(stage, STAGES, "final"),
                "tier",
                normalize(tier, TIERS, "none"),
                "method",
                normalize(method, METHODS, "none"),
                "status",
                normalize(status, STATUSES, "unknown"),
                "source",
                normalize(source, SOURCES, "core"));
    }

    private static String normalize(String value, Set<String> allowed, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }
}
