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
package com.openmemind.ai.memory.core.extraction.result;

import com.openmemind.ai.memory.core.data.MemoryInsight;
import java.util.List;

/**
 * Insight generation result
 *
 * @param insights generated insights list
 */
public record InsightResult(List<MemoryInsight> insights) {

    /**
     * Create empty result
     */
    public static InsightResult empty() {
        return new InsightResult(List.of());
    }

    /**
     * Is empty
     */
    public boolean isEmpty() {
        return insights.isEmpty();
    }

    /**
     * Number of insights
     */
    public int totalCount() {
        return insights.size();
    }
}
