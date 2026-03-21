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
package com.openmemind.ai.memory.core.store;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Memory data persistence storage interface.
 */
public interface MemoryStore {

    void upsertRawData(MemoryId id, List<MemoryRawData> rawDataList);

    Optional<MemoryRawData> getRawData(MemoryId id, String rawDataId);

    Optional<MemoryRawData> getRawDataByContentId(MemoryId id, String contentId);

    List<MemoryRawData> listRawData(MemoryId id);

    List<MemoryRawData> pollRawDataWithoutVector(MemoryId id, int limit, Duration minAge);

    void updateRawDataVectorIds(
            MemoryId id, Map<String, String> vectorIds, Map<String, Object> metadataPatch);

    void insertItems(MemoryId id, List<MemoryItem> items);

    List<MemoryItem> getItemsByIds(MemoryId id, Collection<Long> itemIds);

    List<MemoryItem> getItemsByVectorIds(MemoryId id, Collection<String> vectorIds);

    List<MemoryItem> getItemsByContentHashes(MemoryId id, Collection<String> contentHashes);

    List<MemoryItem> listItems(MemoryId id);

    boolean hasItems(MemoryId id);

    void deleteItems(MemoryId id, Collection<Long> itemIds);

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
