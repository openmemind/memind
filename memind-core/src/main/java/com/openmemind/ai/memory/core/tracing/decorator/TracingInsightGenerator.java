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
package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_INSIGHT_GROUP_NAME;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_INSIGHT_LEAF_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_INSIGHT_POINT_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.EXTRACTION_INSIGHT_TYPE;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointGenerateResponse;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link InsightGenerator}.
 *
 * <p>{@link #generatePoints} wraps with a leaf span, {@link #generateBranchSummary} with a branch
 * span, and {@link #generateRootSynthesis} with a root span, each recording span attributes.
 */
public class TracingInsightGenerator extends TracingSupport implements InsightGenerator {

    private final InsightGenerator delegate;

    public TracingInsightGenerator(InsightGenerator delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<InsightPointGenerateResponse> generatePoints(
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens,
            String additionalContext,
            String language) {
        return trace(
                MemorySpanNames.EXTRACTION_INSIGHT_GENERATE_LEAF,
                Map.of(
                        EXTRACTION_INSIGHT_TYPE,
                        insightType.name(),
                        EXTRACTION_INSIGHT_GROUP_NAME,
                        groupName != null ? groupName : ""),
                r -> Map.of(EXTRACTION_INSIGHT_POINT_COUNT, r.points().size()),
                () ->
                        delegate.generatePoints(
                                insightType,
                                groupName,
                                existingPoints,
                                newItems,
                                targetTokens,
                                additionalContext,
                                language));
    }

    @Override
    public Mono<InsightPointGenerateResponse> generateBranchSummary(
            MemoryInsightType insightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> leafInsights,
            int targetTokens,
            String language) {
        return trace(
                MemorySpanNames.EXTRACTION_INSIGHT_GENERATE_BRANCH,
                Map.of(
                        EXTRACTION_INSIGHT_TYPE,
                        insightType.name(),
                        EXTRACTION_INSIGHT_LEAF_COUNT,
                        leafInsights.size()),
                r -> Map.of(EXTRACTION_INSIGHT_POINT_COUNT, r.points().size()),
                () ->
                        delegate.generateBranchSummary(
                                insightType, existingPoints, leafInsights, targetTokens, language));
    }

    @Override
    public Mono<InsightPointGenerateResponse> generateRootSynthesis(
            MemoryInsightType rootInsightType,
            String existingSummary,
            List<MemoryInsight> branchInsights,
            int targetTokens,
            String language) {
        return trace(
                MemorySpanNames.EXTRACTION_INSIGHT_GENERATE_ROOT,
                Map.of(
                        EXTRACTION_INSIGHT_TYPE,
                        rootInsightType.name(),
                        EXTRACTION_INSIGHT_LEAF_COUNT,
                        branchInsights.size()),
                r -> Map.of(EXTRACTION_INSIGHT_POINT_COUNT, r.points().size()),
                () ->
                        delegate.generateRootSynthesis(
                                rootInsightType,
                                existingSummary,
                                branchInsights,
                                targetTokens,
                                language));
    }
}
