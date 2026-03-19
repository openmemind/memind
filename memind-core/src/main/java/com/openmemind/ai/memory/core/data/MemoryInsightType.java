package com.openmemind.ai.memory.core.data;

import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MemoryInsightType(
        Long id,
        String memoryId,

        /* Name */
        String name,

        /* Description */
        String description,

        /* description Vector Library ID */
        String descriptionVectorId,

        /* Associated category list */
        List<String> categories,

        /* summary token budget */
        int targetTokens,

        /* Custom summary prompt sections, key is section name, value is content text, use default when null */
        Map<String, String> summaryPrompt,

        /* Last updated time */
        Instant lastUpdatedAt,

        /* Creation time */
        Instant createdAt,

        /* Update time */
        Instant updatedAt,

        /* Analysis mode (default SUMMARIZE) */
        InsightAnalysisMode insightAnalysisMode,

        /* Tree configuration (nullable, use default value when null) */
        InsightTreeConfig treeConfig,

        /* Scope (null indicates ROOT type, across scope) */
        MemoryScope scope,

        /* Accepted content type list (nullable, null=accept all types) */
        List<String> acceptContentTypes) {

    /** Resolve tree configuration, return default value when null */
    public InsightTreeConfig resolveTreeConfig() {
        return treeConfig != null ? treeConfig : InsightTreeConfig.defaults();
    }

    /** Return a copy with different targetTokens */
    public MemoryInsightType withTargetTokens(int targetTokens) {
        return new MemoryInsightType(
                id,
                memoryId,
                name,
                description,
                descriptionVectorId,
                categories,
                targetTokens,
                summaryPrompt,
                lastUpdatedAt,
                createdAt,
                updatedAt,
                insightAnalysisMode,
                treeConfig,
                scope,
                acceptContentTypes);
    }

    /** Return a copy with different treeConfig */
    public MemoryInsightType withTreeConfig(InsightTreeConfig treeConfig) {
        return new MemoryInsightType(
                id,
                memoryId,
                name,
                description,
                descriptionVectorId,
                categories,
                targetTokens,
                summaryPrompt,
                lastUpdatedAt,
                createdAt,
                updatedAt,
                insightAnalysisMode,
                treeConfig,
                scope,
                acceptContentTypes);
    }
}
