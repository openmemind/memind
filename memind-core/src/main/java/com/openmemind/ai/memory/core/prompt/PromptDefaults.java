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
package com.openmemind.ai.memory.core.prompt;

import com.openmemind.ai.memory.core.prompt.extraction.insight.BranchAggregationPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.insight.InsightGroupPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.insight.InsightLeafPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.insight.InteractionGuideSynthesisPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.insight.RootSynthesisPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.item.ForesightPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.item.MemoryItemUnifiedPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.item.SelfVerificationPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.item.ToolItemPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.rawdata.BoundaryDetectionPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.rawdata.CaptionPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.rawdata.ConversationSegmentationPrompts;
import com.openmemind.ai.memory.core.prompt.extraction.thread.ThreadEnrichmentPrompts;
import com.openmemind.ai.memory.core.prompt.retrieval.InsightTypeRoutingPrompts;
import com.openmemind.ai.memory.core.prompt.retrieval.IntentRoutingPrompts;
import com.openmemind.ai.memory.core.prompt.retrieval.LongQueryCondensePrompts;
import com.openmemind.ai.memory.core.prompt.retrieval.QueryRewritePrompts;
import com.openmemind.ai.memory.core.prompt.retrieval.SufficiencyGatePrompts;
import com.openmemind.ai.memory.core.prompt.retrieval.TypedQueryExpandPrompts;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Supplier;

/** Package-private catalog of default prompt templates. */
final class PromptDefaults {

    private static final EnumMap<PromptType, PromptDefaultEntry> DEFAULTS = createDefaults();

    private PromptDefaults() {}

    static PromptTemplate build(PromptType type) {
        return getEntry(type).defaultBuilder().get();
    }

    static PromptTemplate buildPreview(PromptType type) {
        return getEntry(type).previewBuilder().get();
    }

    private static PromptDefaultEntry getEntry(PromptType type) {
        Objects.requireNonNull(type, "type");
        PromptDefaultEntry entry = DEFAULTS.get(type);
        if (entry == null) {
            throw new UnsupportedOperationException("Default prompt not implemented for " + type);
        }
        return entry;
    }

    private static EnumMap<PromptType, PromptDefaultEntry> createDefaults() {
        var builders = new EnumMap<PromptType, PromptDefaultEntry>(PromptType.class);
        builders.put(PromptType.INTENT_ROUTING, entry(IntentRoutingPrompts::buildDefault));
        builders.put(PromptType.QUERY_REWRITE, entry(QueryRewritePrompts::buildDefault));
        builders.put(PromptType.LONG_QUERY_CONDENSE, entry(LongQueryCondensePrompts::buildDefault));
        builders.put(
                PromptType.TYPED_QUERY_EXPAND,
                entry(
                        TypedQueryExpandPrompts::buildDefault,
                        TypedQueryExpandPrompts::buildPreview));
        builders.put(
                PromptType.INSIGHT_TYPE_ROUTING, entry(InsightTypeRoutingPrompts::buildDefault));
        builders.put(PromptType.SUFFICIENCY_GATE, entry(SufficiencyGatePrompts::buildDefault));
        builders.put(
                PromptType.MEMORY_ITEM_UNIFIED,
                entry(
                        MemoryItemUnifiedPrompts::buildDefault,
                        MemoryItemUnifiedPrompts::buildPreview));
        builders.put(PromptType.TOOL_ITEM, entry(ToolItemPrompts::buildDefault));
        builders.put(PromptType.FORESIGHT, entry(ForesightPrompts::buildDefault));
        builders.put(
                PromptType.SELF_VERIFICATION,
                entry(
                        SelfVerificationPrompts::buildDefault,
                        SelfVerificationPrompts::buildPreview));
        builders.put(PromptType.CAPTION, entry(CaptionPrompts::buildDefault));
        builders.put(PromptType.BOUNDARY_DETECTION, entry(BoundaryDetectionPrompts::buildDefault));
        builders.put(
                PromptType.CONVERSATION_SEGMENTATION,
                entry(ConversationSegmentationPrompts::buildDefault));
        builders.put(
                PromptType.INSIGHT_GROUP,
                entry(InsightGroupPrompts::buildDefault, InsightGroupPrompts::buildPreview));
        builders.put(
                PromptType.INSIGHT_LEAF,
                entry(InsightLeafPrompts::buildDefault, InsightLeafPrompts::buildPreview));
        builders.put(
                PromptType.INSIGHT_LEAF_POINT_OPS,
                entry(
                        InsightLeafPrompts::buildPointOpsDefault,
                        InsightLeafPrompts::buildPointOpsPreview));
        builders.put(
                PromptType.BRANCH_AGGREGATION,
                entry(
                        BranchAggregationPrompts::buildDefault,
                        BranchAggregationPrompts::buildPreview));
        builders.put(
                PromptType.BRANCH_AGGREGATION_POINT_OPS,
                entry(
                        BranchAggregationPrompts::buildPointOpsDefault,
                        BranchAggregationPrompts::buildPointOpsPreview));
        builders.put(
                PromptType.ROOT_SYNTHESIS,
                entry(RootSynthesisPrompts::buildDefault, RootSynthesisPrompts::buildPreview));
        builders.put(
                PromptType.INTERACTION_GUIDE_SYNTHESIS,
                entry(
                        InteractionGuideSynthesisPrompts::buildDefault,
                        InteractionGuideSynthesisPrompts::buildPreview));
        builders.put(
                PromptType.THREAD_ENRICHMENT,
                entry(
                        ThreadEnrichmentPrompts::buildDefault,
                        ThreadEnrichmentPrompts::buildPreview));
        validateExhaustive(builders);
        return builders;
    }

    private static PromptDefaultEntry entry(Supplier<PromptTemplate> defaultBuilder) {
        return new PromptDefaultEntry(defaultBuilder, defaultBuilder);
    }

    private static PromptDefaultEntry entry(
            Supplier<PromptTemplate> defaultBuilder, Supplier<PromptTemplate> previewBuilder) {
        return new PromptDefaultEntry(defaultBuilder, previewBuilder);
    }

    private static void validateExhaustive(EnumMap<PromptType, PromptDefaultEntry> builders) {
        var missing = EnumSet.allOf(PromptType.class);
        missing.removeAll(builders.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing default prompt builders for prompt types: " + missing);
        }
    }

    private record PromptDefaultEntry(
            Supplier<PromptTemplate> defaultBuilder, Supplier<PromptTemplate> previewBuilder) {}
}
