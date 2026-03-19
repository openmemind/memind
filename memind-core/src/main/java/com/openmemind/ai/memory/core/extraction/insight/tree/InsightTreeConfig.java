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
package com.openmemind.ai.memory.core.extraction.insight.tree;

/**
 * Insight shared tree configuration
 *
 * <p>Fixed three-layer structure (Leaf → Branch → Root), controlled by bubble threshold for automatic reorganization.
 *
 * @param branchBubbleThreshold LEAF changes how many times before re-summarizing BRANCH
 * @param rootBubbleThreshold   BRANCH changes how many times before re-summarizing ROOT
 * @param minBranchesForRoot    Minimum number of BRANCH required to create ROOT
 * @param rootTargetTokens      Token budget for ROOT re-summarization
 */
public record InsightTreeConfig(
        int branchBubbleThreshold,
        int rootBubbleThreshold,
        int minBranchesForRoot,
        int rootTargetTokens) {

    public static InsightTreeConfig defaults() {
        return new InsightTreeConfig(3, 2, 2, 800);
    }
}
