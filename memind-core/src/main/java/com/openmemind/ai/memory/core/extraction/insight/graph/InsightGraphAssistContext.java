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
package com.openmemind.ai.memory.core.extraction.insight.graph;

import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryItem;
import java.util.List;

/**
 * Shared graph-assist contexts used by insight grouping and synthesis stages.
 */
public final class InsightGraphAssistContext {

    private InsightGraphAssistContext() {}

    public record GroupingAssist(String additionalContext) {

        public GroupingAssist {
            additionalContext = additionalContext == null ? "" : additionalContext;
        }

        public static GroupingAssist empty() {
            return new GroupingAssist("");
        }
    }

    public record LeafAssist(List<MemoryItem> orderedItems, String additionalContext) {

        public LeafAssist {
            orderedItems = orderedItems == null ? List.of() : List.copyOf(orderedItems);
            additionalContext = additionalContext == null ? "" : additionalContext;
        }

        public static LeafAssist identity(List<MemoryItem> items) {
            return new LeafAssist(items, "");
        }
    }

    public record BranchAssist(List<MemoryInsight> orderedLeafInsights, String additionalContext) {

        public BranchAssist {
            orderedLeafInsights =
                    orderedLeafInsights == null ? List.of() : List.copyOf(orderedLeafInsights);
            additionalContext = additionalContext == null ? "" : additionalContext;
        }

        public static BranchAssist identity(List<MemoryInsight> leafInsights) {
            return new BranchAssist(leafInsights, "");
        }
    }

    public record RootAssist(List<MemoryInsight> orderedBranchInsights, String additionalContext) {

        public RootAssist {
            orderedBranchInsights =
                    orderedBranchInsights == null ? List.of() : List.copyOf(orderedBranchInsights);
            additionalContext = additionalContext == null ? "" : additionalContext;
        }

        public static RootAssist identity(List<MemoryInsight> branchInsights) {
            return new RootAssist(branchInsights, "");
        }
    }
}
