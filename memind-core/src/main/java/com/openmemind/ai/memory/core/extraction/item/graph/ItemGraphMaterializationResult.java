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
package com.openmemind.ai.memory.core.extraction.item.graph;

import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionDiagnostics;
import com.openmemind.ai.memory.core.extraction.item.graph.link.semantic.SemanticItemLinker;
import com.openmemind.ai.memory.core.extraction.item.graph.link.temporal.TemporalItemLinker;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Batch-level item+graph materialization outcome returned from the commit-critical graph stage.
 */
public record ItemGraphMaterializationResult(Stats stats) {

    public ItemGraphMaterializationResult {
        stats = stats != null ? stats : Stats.empty();
    }

    public static ItemGraphMaterializationResult empty() {
        return new ItemGraphMaterializationResult(Stats.empty());
    }

    public ItemGraphMaterializationResult withDerivedMaintenanceDegraded() {
        return new ItemGraphMaterializationResult(stats.withDerivedMaintenanceDegraded());
    }

    public record Stats(
            int entityCount,
            int mentionCount,
            int structuredItemLinkCount,
            int temporalSourceCount,
            int temporalHistoryQueryBatchCount,
            int temporalHistoryCandidateCount,
            int temporalIntraBatchCandidateCount,
            int temporalSelectedPairCount,
            int temporalCreatedLinkCount,
            long temporalQueryDurationMs,
            long temporalBuildDurationMs,
            long temporalUpsertDurationMs,
            int temporalBelowRetrievalFloorCount,
            double temporalMinStrength,
            double temporalMaxStrength,
            String temporalStrengthBucketSummary,
            boolean temporalDegraded,
            int resolutionCandidateCount,
            String resolutionCandidateSourceSummary,
            String resolutionMergeScoreHistogramSummary,
            int resolutionCandidateRejectedCount,
            int resolutionMergeAcceptedCount,
            int resolutionMergeRejectedCount,
            int resolutionCreateNewCount,
            int resolutionExactFallbackCount,
            int resolutionCandidateCapHitCount,
            int aliasEvidenceObservedCount,
            int aliasEvidenceMergedCount,
            int resolutionSpecialBypassCount,
            int semanticSearchRequestCount,
            int semanticSearchInvocationCount,
            int semanticSearchHitCount,
            int semanticResolvedCandidateCount,
            int semanticLinkCount,
            int semanticUpsertBatchCount,
            int semanticSourceWindowCount,
            int semanticFailedResolveChunkCount,
            int semanticFailedWindowCount,
            int semanticFailedUpsertBatchCount,
            int semanticSameBatchHitCount,
            int semanticSearchFallbackCount,
            int semanticIntraBatchCandidateCount,
            long semanticSearchPhaseDurationMs,
            long semanticResolvePhaseDurationMs,
            long semanticUpsertPhaseDurationMs,
            long semanticIntraBatchPhaseDurationMs,
            boolean semanticDegraded,
            int typeFallbackToOtherCount,
            String topUnresolvedTypeLabelsSummary,
            int droppedBlankCount,
            int droppedPunctuationOnlyCount,
            int droppedPronounLikeCount,
            int droppedTemporalCount,
            int droppedDateLikeCount,
            int droppedReservedSpecialCollisionCount,
            boolean structuredBatchDegraded,
            boolean derivedMaintenanceDegraded,
            FinalRelationStats finalRelationStats,
            EntityOverlapStats entityOverlapStats) {

        public Stats {
            temporalStrengthBucketSummary =
                    temporalStrengthBucketSummary == null ? "" : temporalStrengthBucketSummary;
            resolutionCandidateSourceSummary =
                    resolutionCandidateSourceSummary == null
                            ? ""
                            : resolutionCandidateSourceSummary;
            resolutionMergeScoreHistogramSummary =
                    resolutionMergeScoreHistogramSummary == null
                            ? ""
                            : resolutionMergeScoreHistogramSummary;
            topUnresolvedTypeLabelsSummary =
                    topUnresolvedTypeLabelsSummary == null ? "" : topUnresolvedTypeLabelsSummary;
            finalRelationStats =
                    finalRelationStats == null ? FinalRelationStats.empty() : finalRelationStats;
            entityOverlapStats =
                    entityOverlapStats == null ? EntityOverlapStats.empty() : entityOverlapStats;
        }

        public record FinalRelationStats(
                int semanticRelationCount,
                int temporalRelationCount,
                int causalRelationCount,
                int itemLinkCount) {

            public static FinalRelationStats empty() {
                return new FinalRelationStats(0, 0, 0, 0);
            }
        }

        public record EntityOverlapStats(
                int candidatePairCount,
                int createdLinkCount,
                int skippedFanoutEntityCount,
                int duplicateSuppressedCount) {

            public static EntityOverlapStats empty() {
                return new EntityOverlapStats(0, 0, 0, 0);
            }
        }

        public Stats(
                int entityCount,
                int mentionCount,
                int structuredItemLinkCount,
                int semanticSearchRequestCount,
                int semanticSearchInvocationCount,
                int semanticSearchHitCount,
                int semanticResolvedCandidateCount,
                int semanticLinkCount,
                int semanticUpsertBatchCount,
                int semanticSourceWindowCount,
                int semanticFailedResolveChunkCount,
                int semanticFailedWindowCount,
                int semanticFailedUpsertBatchCount,
                int semanticSameBatchHitCount,
                int semanticSearchFallbackCount,
                long semanticSearchPhaseDurationMs,
                long semanticResolvePhaseDurationMs,
                long semanticUpsertPhaseDurationMs,
                boolean semanticDegraded,
                int typeFallbackToOtherCount,
                String topUnresolvedTypeLabelsSummary,
                int droppedBlankCount,
                int droppedPunctuationOnlyCount,
                int droppedPronounLikeCount,
                int droppedTemporalCount,
                int droppedDateLikeCount,
                int droppedReservedSpecialCollisionCount) {
            this(
                    entityCount,
                    mentionCount,
                    structuredItemLinkCount,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    0,
                    0.0d,
                    0.0d,
                    "",
                    false,
                    0,
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    semanticSearchRequestCount,
                    semanticSearchInvocationCount,
                    semanticSearchHitCount,
                    semanticResolvedCandidateCount,
                    semanticLinkCount,
                    semanticUpsertBatchCount,
                    semanticSourceWindowCount,
                    semanticFailedResolveChunkCount,
                    semanticFailedWindowCount,
                    semanticFailedUpsertBatchCount,
                    semanticSameBatchHitCount,
                    semanticSearchFallbackCount,
                    0,
                    semanticSearchPhaseDurationMs,
                    semanticResolvePhaseDurationMs,
                    semanticUpsertPhaseDurationMs,
                    0L,
                    semanticDegraded,
                    typeFallbackToOtherCount,
                    topUnresolvedTypeLabelsSummary,
                    droppedBlankCount,
                    droppedPunctuationOnlyCount,
                    droppedPronounLikeCount,
                    droppedTemporalCount,
                    droppedDateLikeCount,
                    droppedReservedSpecialCollisionCount,
                    false,
                    false,
                    FinalRelationStats.empty(),
                    EntityOverlapStats.empty());
        }

        public Stats(
                int entityCount,
                int mentionCount,
                int structuredItemLinkCount,
                int temporalSourceCount,
                int temporalHistoryQueryBatchCount,
                int temporalHistoryCandidateCount,
                int temporalIntraBatchCandidateCount,
                int temporalSelectedPairCount,
                int temporalCreatedLinkCount,
                long temporalQueryDurationMs,
                long temporalBuildDurationMs,
                long temporalUpsertDurationMs,
                boolean temporalDegraded,
                int semanticSearchRequestCount,
                int semanticSearchInvocationCount,
                int semanticSearchHitCount,
                int semanticResolvedCandidateCount,
                int semanticLinkCount,
                int semanticUpsertBatchCount,
                int semanticSourceWindowCount,
                int semanticFailedResolveChunkCount,
                int semanticFailedWindowCount,
                int semanticFailedUpsertBatchCount,
                int semanticSameBatchHitCount,
                int semanticSearchFallbackCount,
                long semanticSearchPhaseDurationMs,
                long semanticResolvePhaseDurationMs,
                long semanticUpsertPhaseDurationMs,
                boolean semanticDegraded,
                int typeFallbackToOtherCount,
                String topUnresolvedTypeLabelsSummary,
                int droppedBlankCount,
                int droppedPunctuationOnlyCount,
                int droppedPronounLikeCount,
                int droppedTemporalCount,
                int droppedDateLikeCount,
                int droppedReservedSpecialCollisionCount) {
            this(
                    entityCount,
                    mentionCount,
                    structuredItemLinkCount,
                    temporalSourceCount,
                    temporalHistoryQueryBatchCount,
                    temporalHistoryCandidateCount,
                    temporalIntraBatchCandidateCount,
                    temporalSelectedPairCount,
                    temporalCreatedLinkCount,
                    temporalQueryDurationMs,
                    temporalBuildDurationMs,
                    temporalUpsertDurationMs,
                    0,
                    0.0d,
                    0.0d,
                    "",
                    temporalDegraded,
                    0,
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    semanticSearchRequestCount,
                    semanticSearchInvocationCount,
                    semanticSearchHitCount,
                    semanticResolvedCandidateCount,
                    semanticLinkCount,
                    semanticUpsertBatchCount,
                    semanticSourceWindowCount,
                    semanticFailedResolveChunkCount,
                    semanticFailedWindowCount,
                    semanticFailedUpsertBatchCount,
                    semanticSameBatchHitCount,
                    semanticSearchFallbackCount,
                    0,
                    semanticSearchPhaseDurationMs,
                    semanticResolvePhaseDurationMs,
                    semanticUpsertPhaseDurationMs,
                    0L,
                    semanticDegraded,
                    typeFallbackToOtherCount,
                    topUnresolvedTypeLabelsSummary,
                    droppedBlankCount,
                    droppedPunctuationOnlyCount,
                    droppedPronounLikeCount,
                    droppedTemporalCount,
                    droppedDateLikeCount,
                    droppedReservedSpecialCollisionCount,
                    false,
                    false,
                    FinalRelationStats.empty(),
                    EntityOverlapStats.empty());
        }

        public Stats(
                int entityCount,
                int mentionCount,
                int structuredItemLinkCount,
                int resolutionCandidateCount,
                String resolutionCandidateSourceSummary,
                String resolutionMergeScoreHistogramSummary,
                int resolutionCandidateRejectedCount,
                int resolutionMergeAcceptedCount,
                int resolutionMergeRejectedCount,
                int resolutionCreateNewCount,
                int resolutionExactFallbackCount,
                int resolutionCandidateCapHitCount,
                int aliasEvidenceObservedCount,
                int aliasEvidenceMergedCount,
                int resolutionSpecialBypassCount,
                int semanticSearchRequestCount,
                int semanticSearchInvocationCount,
                int semanticSearchHitCount,
                int semanticResolvedCandidateCount,
                int semanticLinkCount,
                int semanticUpsertBatchCount,
                int semanticSourceWindowCount,
                int semanticFailedResolveChunkCount,
                int semanticFailedWindowCount,
                int semanticFailedUpsertBatchCount,
                int semanticSameBatchHitCount,
                int semanticSearchFallbackCount,
                int semanticIntraBatchCandidateCount,
                long semanticSearchPhaseDurationMs,
                long semanticResolvePhaseDurationMs,
                long semanticUpsertPhaseDurationMs,
                long semanticIntraBatchPhaseDurationMs,
                boolean semanticDegraded,
                int typeFallbackToOtherCount,
                String topUnresolvedTypeLabelsSummary,
                int droppedBlankCount,
                int droppedPunctuationOnlyCount,
                int droppedPronounLikeCount,
                int droppedTemporalCount,
                int droppedDateLikeCount,
                int droppedReservedSpecialCollisionCount) {
            this(
                    entityCount,
                    mentionCount,
                    structuredItemLinkCount,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    0,
                    0.0d,
                    0.0d,
                    "",
                    false,
                    resolutionCandidateCount,
                    resolutionCandidateSourceSummary,
                    resolutionMergeScoreHistogramSummary,
                    resolutionCandidateRejectedCount,
                    resolutionMergeAcceptedCount,
                    resolutionMergeRejectedCount,
                    resolutionCreateNewCount,
                    resolutionExactFallbackCount,
                    resolutionCandidateCapHitCount,
                    aliasEvidenceObservedCount,
                    aliasEvidenceMergedCount,
                    resolutionSpecialBypassCount,
                    semanticSearchRequestCount,
                    semanticSearchInvocationCount,
                    semanticSearchHitCount,
                    semanticResolvedCandidateCount,
                    semanticLinkCount,
                    semanticUpsertBatchCount,
                    semanticSourceWindowCount,
                    semanticFailedResolveChunkCount,
                    semanticFailedWindowCount,
                    semanticFailedUpsertBatchCount,
                    semanticSameBatchHitCount,
                    semanticSearchFallbackCount,
                    semanticIntraBatchCandidateCount,
                    semanticSearchPhaseDurationMs,
                    semanticResolvePhaseDurationMs,
                    semanticUpsertPhaseDurationMs,
                    semanticIntraBatchPhaseDurationMs,
                    semanticDegraded,
                    typeFallbackToOtherCount,
                    topUnresolvedTypeLabelsSummary,
                    droppedBlankCount,
                    droppedPunctuationOnlyCount,
                    droppedPronounLikeCount,
                    droppedTemporalCount,
                    droppedDateLikeCount,
                    droppedReservedSpecialCollisionCount,
                    false,
                    false,
                    FinalRelationStats.empty(),
                    EntityOverlapStats.empty());
        }

        public static Stats withTemporalAndSemantic(
                int entityCount,
                int mentionCount,
                int structuredItemLinkCount,
                TemporalItemLinker.TemporalLinkingStats temporalStats,
                EntityResolutionDiagnostics resolutionDiagnostics,
                SemanticItemLinker.SemanticLinkingStats semanticStats,
                int typeFallbackToOtherCount,
                String topUnresolvedTypeLabelsSummary,
                int droppedBlankCount,
                int droppedPunctuationOnlyCount,
                int droppedPronounLikeCount,
                int droppedTemporalCount,
                int droppedDateLikeCount,
                int droppedReservedSpecialCollisionCount) {
            TemporalItemLinker.TemporalLinkingStats effectiveTemporalStats =
                    temporalStats != null
                            ? temporalStats
                            : TemporalItemLinker.TemporalLinkingStats.empty();
            EntityResolutionDiagnostics effectiveResolutionDiagnostics =
                    resolutionDiagnostics != null
                            ? resolutionDiagnostics
                            : EntityResolutionDiagnostics.empty();
            SemanticItemLinker.SemanticLinkingStats effectiveSemanticStats =
                    semanticStats != null
                            ? semanticStats
                            : SemanticItemLinker.SemanticLinkingStats.empty();
            return new Stats(
                    entityCount,
                    mentionCount,
                    structuredItemLinkCount,
                    effectiveTemporalStats.sourceCount(),
                    effectiveTemporalStats.historyQueryBatchCount(),
                    effectiveTemporalStats.historyCandidateCount(),
                    effectiveTemporalStats.intraBatchCandidateCount(),
                    effectiveTemporalStats.selectedPairCount(),
                    effectiveTemporalStats.createdLinkCount(),
                    effectiveTemporalStats.queryDurationMs(),
                    effectiveTemporalStats.buildDurationMs(),
                    effectiveTemporalStats.upsertDurationMs(),
                    effectiveTemporalStats.belowRetrievalFloorCount(),
                    effectiveTemporalStats.minStrength(),
                    effectiveTemporalStats.maxStrength(),
                    effectiveTemporalStats.strengthBucketSummary(),
                    effectiveTemporalStats.degraded(),
                    effectiveResolutionDiagnostics.candidateCount(),
                    summarizeCountMap(effectiveResolutionDiagnostics.candidateSourceCounts(), 5),
                    effectiveResolutionDiagnostics.mergeScoreHistogramSummary(),
                    sumValues(effectiveResolutionDiagnostics.candidateRejectCounts()),
                    effectiveResolutionDiagnostics.mergeAcceptedCount(),
                    effectiveResolutionDiagnostics.mergeRejectedCount(),
                    effectiveResolutionDiagnostics.createNewCount(),
                    effectiveResolutionDiagnostics.exactFallbackCount(),
                    effectiveResolutionDiagnostics.candidateCapHitCount(),
                    effectiveResolutionDiagnostics.aliasEvidenceObservedCount(),
                    effectiveResolutionDiagnostics.aliasEvidenceMergedCount(),
                    effectiveResolutionDiagnostics.specialBypassCount(),
                    effectiveSemanticStats.searchRequestCount(),
                    effectiveSemanticStats.searchInvocationCount(),
                    effectiveSemanticStats.searchHitCount(),
                    effectiveSemanticStats.resolvedCandidateCount(),
                    effectiveSemanticStats.createdLinkCount(),
                    effectiveSemanticStats.upsertBatchCount(),
                    effectiveSemanticStats.sourceWindowCount(),
                    effectiveSemanticStats.failedResolveChunkCount(),
                    effectiveSemanticStats.failedWindowCount(),
                    effectiveSemanticStats.failedUpsertBatchCount(),
                    effectiveSemanticStats.sameBatchHitCount(),
                    effectiveSemanticStats.searchFallbackCount(),
                    effectiveSemanticStats.intraBatchCandidateCount(),
                    effectiveSemanticStats.searchPhaseDurationMs(),
                    effectiveSemanticStats.resolvePhaseDurationMs(),
                    effectiveSemanticStats.upsertPhaseDurationMs(),
                    effectiveSemanticStats.intraBatchPhaseDurationMs(),
                    effectiveSemanticStats.degraded(),
                    typeFallbackToOtherCount,
                    topUnresolvedTypeLabelsSummary,
                    droppedBlankCount,
                    droppedPunctuationOnlyCount,
                    droppedPronounLikeCount,
                    droppedTemporalCount,
                    droppedDateLikeCount,
                    droppedReservedSpecialCollisionCount,
                    false,
                    false,
                    FinalRelationStats.empty(),
                    EntityOverlapStats.empty());
        }

        Builder toBuilder() {
            return new Builder(this);
        }

        public Stats withStructuredBatchDegraded(boolean structuredBatchDegraded) {
            return toBuilder().structuredBatchDegraded(structuredBatchDegraded).build();
        }

        public Stats withDerivedMaintenanceDegraded() {
            return toBuilder().derivedMaintenanceDegraded(true).build();
        }

        public Stats withFinalRelationStats(FinalRelationStats finalRelationStats) {
            return toBuilder().finalRelationStats(finalRelationStats).build();
        }

        public Stats withEntityOverlapStats(EntityOverlapStats entityOverlapStats) {
            return toBuilder().entityOverlapStats(entityOverlapStats).build();
        }

        public static Stats empty() {
            return withTemporalAndSemantic(
                    0,
                    0,
                    0,
                    TemporalItemLinker.TemporalLinkingStats.empty(),
                    EntityResolutionDiagnostics.empty(),
                    SemanticItemLinker.SemanticLinkingStats.empty(),
                    0,
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0);
        }

        private static String summarizeCountMap(Map<?, Integer> counts, int maxEntries) {
            if (counts == null || counts.isEmpty()) {
                return "";
            }
            return counts.entrySet().stream()
                    .limit(maxEntries)
                    .map(entry -> normalizeSummaryKey(entry.getKey()) + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));
        }

        private static String normalizeSummaryKey(Object key) {
            return String.valueOf(key).toLowerCase(Locale.ROOT);
        }

        private static int sumValues(Map<?, Integer> counts) {
            if (counts == null || counts.isEmpty()) {
                return 0;
            }
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        static final class Builder {

            private int entityCount;
            private int mentionCount;
            private int structuredItemLinkCount;
            private int temporalSourceCount;
            private int temporalHistoryQueryBatchCount;
            private int temporalHistoryCandidateCount;
            private int temporalIntraBatchCandidateCount;
            private int temporalSelectedPairCount;
            private int temporalCreatedLinkCount;
            private long temporalQueryDurationMs;
            private long temporalBuildDurationMs;
            private long temporalUpsertDurationMs;
            private int temporalBelowRetrievalFloorCount;
            private double temporalMinStrength;
            private double temporalMaxStrength;
            private String temporalStrengthBucketSummary;
            private boolean temporalDegraded;
            private int resolutionCandidateCount;
            private String resolutionCandidateSourceSummary;
            private String resolutionMergeScoreHistogramSummary;
            private int resolutionCandidateRejectedCount;
            private int resolutionMergeAcceptedCount;
            private int resolutionMergeRejectedCount;
            private int resolutionCreateNewCount;
            private int resolutionExactFallbackCount;
            private int resolutionCandidateCapHitCount;
            private int aliasEvidenceObservedCount;
            private int aliasEvidenceMergedCount;
            private int resolutionSpecialBypassCount;
            private int semanticSearchRequestCount;
            private int semanticSearchInvocationCount;
            private int semanticSearchHitCount;
            private int semanticResolvedCandidateCount;
            private int semanticLinkCount;
            private int semanticUpsertBatchCount;
            private int semanticSourceWindowCount;
            private int semanticFailedResolveChunkCount;
            private int semanticFailedWindowCount;
            private int semanticFailedUpsertBatchCount;
            private int semanticSameBatchHitCount;
            private int semanticSearchFallbackCount;
            private int semanticIntraBatchCandidateCount;
            private long semanticSearchPhaseDurationMs;
            private long semanticResolvePhaseDurationMs;
            private long semanticUpsertPhaseDurationMs;
            private long semanticIntraBatchPhaseDurationMs;
            private boolean semanticDegraded;
            private int typeFallbackToOtherCount;
            private String topUnresolvedTypeLabelsSummary;
            private int droppedBlankCount;
            private int droppedPunctuationOnlyCount;
            private int droppedPronounLikeCount;
            private int droppedTemporalCount;
            private int droppedDateLikeCount;
            private int droppedReservedSpecialCollisionCount;
            private boolean structuredBatchDegraded;
            private boolean derivedMaintenanceDegraded;
            private FinalRelationStats finalRelationStats = FinalRelationStats.empty();
            private EntityOverlapStats entityOverlapStats = EntityOverlapStats.empty();

            private Builder(Stats stats) {
                this.entityCount = stats.entityCount();
                this.mentionCount = stats.mentionCount();
                this.structuredItemLinkCount = stats.structuredItemLinkCount();
                this.temporalSourceCount = stats.temporalSourceCount();
                this.temporalHistoryQueryBatchCount = stats.temporalHistoryQueryBatchCount();
                this.temporalHistoryCandidateCount = stats.temporalHistoryCandidateCount();
                this.temporalIntraBatchCandidateCount = stats.temporalIntraBatchCandidateCount();
                this.temporalSelectedPairCount = stats.temporalSelectedPairCount();
                this.temporalCreatedLinkCount = stats.temporalCreatedLinkCount();
                this.temporalQueryDurationMs = stats.temporalQueryDurationMs();
                this.temporalBuildDurationMs = stats.temporalBuildDurationMs();
                this.temporalUpsertDurationMs = stats.temporalUpsertDurationMs();
                this.temporalBelowRetrievalFloorCount = stats.temporalBelowRetrievalFloorCount();
                this.temporalMinStrength = stats.temporalMinStrength();
                this.temporalMaxStrength = stats.temporalMaxStrength();
                this.temporalStrengthBucketSummary = stats.temporalStrengthBucketSummary();
                this.temporalDegraded = stats.temporalDegraded();
                this.resolutionCandidateCount = stats.resolutionCandidateCount();
                this.resolutionCandidateSourceSummary = stats.resolutionCandidateSourceSummary();
                this.resolutionMergeScoreHistogramSummary =
                        stats.resolutionMergeScoreHistogramSummary();
                this.resolutionCandidateRejectedCount = stats.resolutionCandidateRejectedCount();
                this.resolutionMergeAcceptedCount = stats.resolutionMergeAcceptedCount();
                this.resolutionMergeRejectedCount = stats.resolutionMergeRejectedCount();
                this.resolutionCreateNewCount = stats.resolutionCreateNewCount();
                this.resolutionExactFallbackCount = stats.resolutionExactFallbackCount();
                this.resolutionCandidateCapHitCount = stats.resolutionCandidateCapHitCount();
                this.aliasEvidenceObservedCount = stats.aliasEvidenceObservedCount();
                this.aliasEvidenceMergedCount = stats.aliasEvidenceMergedCount();
                this.resolutionSpecialBypassCount = stats.resolutionSpecialBypassCount();
                this.semanticSearchRequestCount = stats.semanticSearchRequestCount();
                this.semanticSearchInvocationCount = stats.semanticSearchInvocationCount();
                this.semanticSearchHitCount = stats.semanticSearchHitCount();
                this.semanticResolvedCandidateCount = stats.semanticResolvedCandidateCount();
                this.semanticLinkCount = stats.semanticLinkCount();
                this.semanticUpsertBatchCount = stats.semanticUpsertBatchCount();
                this.semanticSourceWindowCount = stats.semanticSourceWindowCount();
                this.semanticFailedResolveChunkCount = stats.semanticFailedResolveChunkCount();
                this.semanticFailedWindowCount = stats.semanticFailedWindowCount();
                this.semanticFailedUpsertBatchCount = stats.semanticFailedUpsertBatchCount();
                this.semanticSameBatchHitCount = stats.semanticSameBatchHitCount();
                this.semanticSearchFallbackCount = stats.semanticSearchFallbackCount();
                this.semanticIntraBatchCandidateCount = stats.semanticIntraBatchCandidateCount();
                this.semanticSearchPhaseDurationMs = stats.semanticSearchPhaseDurationMs();
                this.semanticResolvePhaseDurationMs = stats.semanticResolvePhaseDurationMs();
                this.semanticUpsertPhaseDurationMs = stats.semanticUpsertPhaseDurationMs();
                this.semanticIntraBatchPhaseDurationMs = stats.semanticIntraBatchPhaseDurationMs();
                this.semanticDegraded = stats.semanticDegraded();
                this.typeFallbackToOtherCount = stats.typeFallbackToOtherCount();
                this.topUnresolvedTypeLabelsSummary = stats.topUnresolvedTypeLabelsSummary();
                this.droppedBlankCount = stats.droppedBlankCount();
                this.droppedPunctuationOnlyCount = stats.droppedPunctuationOnlyCount();
                this.droppedPronounLikeCount = stats.droppedPronounLikeCount();
                this.droppedTemporalCount = stats.droppedTemporalCount();
                this.droppedDateLikeCount = stats.droppedDateLikeCount();
                this.droppedReservedSpecialCollisionCount =
                        stats.droppedReservedSpecialCollisionCount();
                this.structuredBatchDegraded = stats.structuredBatchDegraded();
                this.derivedMaintenanceDegraded = stats.derivedMaintenanceDegraded();
                this.finalRelationStats = stats.finalRelationStats();
                this.entityOverlapStats = stats.entityOverlapStats();
            }

            Builder structuredBatchDegraded(boolean structuredBatchDegraded) {
                this.structuredBatchDegraded = structuredBatchDegraded;
                return this;
            }

            Builder derivedMaintenanceDegraded(boolean derivedMaintenanceDegraded) {
                this.derivedMaintenanceDegraded = derivedMaintenanceDegraded;
                return this;
            }

            Builder finalRelationStats(FinalRelationStats finalRelationStats) {
                this.finalRelationStats =
                        finalRelationStats == null
                                ? FinalRelationStats.empty()
                                : finalRelationStats;
                return this;
            }

            Builder entityOverlapStats(EntityOverlapStats entityOverlapStats) {
                this.entityOverlapStats =
                        entityOverlapStats == null
                                ? EntityOverlapStats.empty()
                                : entityOverlapStats;
                return this;
            }

            Stats build() {
                return new Stats(
                        entityCount,
                        mentionCount,
                        structuredItemLinkCount,
                        temporalSourceCount,
                        temporalHistoryQueryBatchCount,
                        temporalHistoryCandidateCount,
                        temporalIntraBatchCandidateCount,
                        temporalSelectedPairCount,
                        temporalCreatedLinkCount,
                        temporalQueryDurationMs,
                        temporalBuildDurationMs,
                        temporalUpsertDurationMs,
                        temporalBelowRetrievalFloorCount,
                        temporalMinStrength,
                        temporalMaxStrength,
                        temporalStrengthBucketSummary,
                        temporalDegraded,
                        resolutionCandidateCount,
                        resolutionCandidateSourceSummary,
                        resolutionMergeScoreHistogramSummary,
                        resolutionCandidateRejectedCount,
                        resolutionMergeAcceptedCount,
                        resolutionMergeRejectedCount,
                        resolutionCreateNewCount,
                        resolutionExactFallbackCount,
                        resolutionCandidateCapHitCount,
                        aliasEvidenceObservedCount,
                        aliasEvidenceMergedCount,
                        resolutionSpecialBypassCount,
                        semanticSearchRequestCount,
                        semanticSearchInvocationCount,
                        semanticSearchHitCount,
                        semanticResolvedCandidateCount,
                        semanticLinkCount,
                        semanticUpsertBatchCount,
                        semanticSourceWindowCount,
                        semanticFailedResolveChunkCount,
                        semanticFailedWindowCount,
                        semanticFailedUpsertBatchCount,
                        semanticSameBatchHitCount,
                        semanticSearchFallbackCount,
                        semanticIntraBatchCandidateCount,
                        semanticSearchPhaseDurationMs,
                        semanticResolvePhaseDurationMs,
                        semanticUpsertPhaseDurationMs,
                        semanticIntraBatchPhaseDurationMs,
                        semanticDegraded,
                        typeFallbackToOtherCount,
                        topUnresolvedTypeLabelsSummary,
                        droppedBlankCount,
                        droppedPunctuationOnlyCount,
                        droppedPronounLikeCount,
                        droppedTemporalCount,
                        droppedDateLikeCount,
                        droppedReservedSpecialCollisionCount,
                        structuredBatchDegraded,
                        derivedMaintenanceDegraded,
                        finalRelationStats,
                        entityOverlapStats);
            }
        }
    }
}
