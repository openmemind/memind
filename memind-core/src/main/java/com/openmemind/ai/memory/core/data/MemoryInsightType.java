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
package com.openmemind.ai.memory.core.data;

import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeConfig;
import java.time.Instant;
import java.util.List;

public record MemoryInsightType(
        Long id,

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
                name,
                description,
                descriptionVectorId,
                categories,
                targetTokens,
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
                name,
                description,
                descriptionVectorId,
                categories,
                targetTokens,
                lastUpdatedAt,
                createdAt,
                updatedAt,
                insightAnalysisMode,
                treeConfig,
                scope,
                acceptContentTypes);
    }
}
