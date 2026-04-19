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
 * Batch-level graph materialization outcome returned from the post-commit item graph stage.
 */
public record ItemGraphMaterializationResult(Stats stats) {

    public ItemGraphMaterializationResult {
        stats = stats != null ? stats : Stats.empty();
    }

    public static ItemGraphMaterializationResult empty() {
        return new ItemGraphMaterializationResult(Stats.empty());
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
            int droppedReservedSpecialCollisionCount) {

        public Stats {
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
                    droppedReservedSpecialCollisionCount);
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
                    droppedReservedSpecialCollisionCount);
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
                    droppedReservedSpecialCollisionCount);
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
                    droppedReservedSpecialCollisionCount);
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
    }
}
