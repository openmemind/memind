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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.insight.graph.InsightGraphAssistContext.BranchAssist;
import com.openmemind.ai.memory.core.extraction.insight.graph.InsightGraphAssistContext.GroupingAssist;
import com.openmemind.ai.memory.core.extraction.insight.graph.InsightGraphAssistContext.LeafAssist;
import com.openmemind.ai.memory.core.extraction.insight.graph.InsightGraphAssistContext.RootAssist;
import java.util.List;

/**
 * Optional graph-derived advisory layer for the existing insight tree pipeline.
 */
public interface InsightGraphAssistant {

    GroupingAssist groupingAssist(
            MemoryId memoryId, MemoryInsightType insightType, List<MemoryItem> items);

    LeafAssist leafAssist(
            MemoryId memoryId,
            MemoryInsightType insightType,
            String groupName,
            List<MemoryItem> items);

    BranchAssist branchAssist(
            MemoryId memoryId, MemoryInsightType insightType, List<MemoryInsight> leafInsights);

    RootAssist rootAssist(
            MemoryId memoryId,
            MemoryInsightType rootInsightType,
            List<MemoryInsight> branchInsights);
}
