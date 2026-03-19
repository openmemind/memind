package com.openmemind.ai.memory.core.extraction.insight.generator;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Insight generator interface
 *
 * <p>Responsible for calling LLM to generate/update Insight points at LEAF, BRANCH, and ROOT
 * levels.
 */
public interface InsightGenerator {

    /**
     * Generate structured InsightPoints (delegated to 7 parameter version)
     */
    default Mono<InsightPointGenerateResponse> generatePoints(
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens) {
        return generatePoints(
                insightType, groupName, existingPoints, newItems, targetTokens, null, null);
    }

    /**
     * Generate structured InsightPoints (supports additional context, delegated to 7 parameter version)
     */
    default Mono<InsightPointGenerateResponse> generatePoints(
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens,
            String additionalContext) {
        return generatePoints(
                insightType,
                groupName,
                existingPoints,
                newItems,
                targetTokens,
                additionalContext,
                null);
    }

    /**
     * Generate structured InsightPoints (full version with additional context and language)
     *
     * @param insightType      Insight type
     * @param groupName        Subgroup name
     * @param existingPoints   Existing points
     * @param newItems         New item list
     * @param targetTokens     Token budget
     * @param additionalContext Additional context, can be null
     * @param language         Output language hint, can be null
     * @return Structured response containing the complete list of points
     */
    Mono<InsightPointGenerateResponse> generatePoints(
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens,
            String additionalContext,
            String language);

    /**
     * Aggregate all LEAFs under InsightType to generate BRANCH summary (delegated to 5 parameter version)
     */
    default Mono<InsightPointGenerateResponse> generateBranchSummary(
            MemoryInsightType insightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> leafInsights,
            int targetTokens) {
        return generateBranchSummary(insightType, existingPoints, leafInsights, targetTokens, null);
    }

    /**
     * Aggregate all LEAFs under InsightType to generate BRANCH summary (with language)
     *
     * @param insightType    Insight type
     * @param existingPoints Existing BRANCH points
     * @param leafInsights   All LEAFs under this type
     * @param targetTokens   Token budget
     * @param language       Output language hint, can be null
     * @return Structured response containing points
     */
    Mono<InsightPointGenerateResponse> generateBranchSummary(
            MemoryInsightType insightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> leafInsights,
            int targetTokens,
            String language);

    /**
     * Synthesize all BRANCHes to generate ROOT deep insight (delegated to 5 parameter version)
     */
    default Mono<InsightPointGenerateResponse> generateRootSynthesis(
            MemoryInsightType rootInsightType,
            String existingSummary,
            List<MemoryInsight> branchInsights,
            int targetTokens) {
        return generateRootSynthesis(
                rootInsightType, existingSummary, branchInsights, targetTokens, null);
    }

    /**
     * Synthesize all BRANCHes to generate ROOT deep insight (with language)
     *
     * @param rootInsightType ROOT mode InsightType
     * @param existingSummary Existing ROOT summary (null for the first time)
     * @param branchInsights  All BRANCHes
     * @param targetTokens    Token budget
     * @param language        Output language hint, can be null
     * @return Structured response containing points
     */
    Mono<InsightPointGenerateResponse> generateRootSynthesis(
            MemoryInsightType rootInsightType,
            String existingSummary,
            List<MemoryInsight> branchInsights,
            int targetTokens,
            String language);
}
