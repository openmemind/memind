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

/** Enumerates all customizable prompt families. */
public enum PromptType {
    INTENT_ROUTING,
    QUERY_REWRITE,
    TYPED_QUERY_EXPAND,
    INSIGHT_TYPE_ROUTING,
    SUFFICIENCY_GATE,
    MEMORY_ITEM_UNIFIED,
    TOOL_ITEM,
    FORESIGHT,
    SELF_VERIFICATION,
    CAPTION,
    BOUNDARY_DETECTION,
    CONVERSATION_SEGMENTATION,
    INSIGHT_GROUP,
    INSIGHT_LEAF,
    INSIGHT_LEAF_POINT_OPS,
    BRANCH_AGGREGATION,
    BRANCH_AGGREGATION_POINT_OPS,
    ROOT_SYNTHESIS,
    INTERACTION_GUIDE_SYNTHESIS,
    THREAD_ENRICHMENT
}
