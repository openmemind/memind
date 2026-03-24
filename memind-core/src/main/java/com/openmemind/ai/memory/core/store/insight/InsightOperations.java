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
package com.openmemind.ai.memory.core.store.insight;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Operations for insight and insight type persistence.
 */
public interface InsightOperations {

    void upsertInsightTypes(List<MemoryInsightType> insightTypes);

    Optional<MemoryInsightType> getInsightType(String insightType);

    List<MemoryInsightType> listInsightTypes();

    void upsertInsights(MemoryId id, List<MemoryInsight> insights);

    Optional<MemoryInsight> getInsight(MemoryId id, Long insightId);

    List<MemoryInsight> listInsights(MemoryId id);

    List<MemoryInsight> getInsightsByType(MemoryId id, String insightType);

    List<MemoryInsight> getInsightsByTier(MemoryId id, InsightTier tier);

    Optional<MemoryInsight> getLeafByGroup(MemoryId id, String type, String group);

    Optional<MemoryInsight> getBranchByType(MemoryId id, String type);

    Optional<MemoryInsight> getRootByType(MemoryId id, String type);

    void deleteInsights(MemoryId id, Collection<Long> insightIds);
}
