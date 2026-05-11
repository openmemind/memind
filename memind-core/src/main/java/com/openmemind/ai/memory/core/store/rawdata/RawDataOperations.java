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
package com.openmemind.ai.memory.core.store.rawdata;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Operations for raw data persistence.
 */
public interface RawDataOperations {

    void upsertRawData(MemoryId id, List<MemoryRawData> rawDataList);

    Optional<MemoryRawData> getRawData(MemoryId id, String rawDataId);

    Optional<MemoryRawData> getRawDataByContentId(MemoryId id, String contentId);

    default List<MemoryRawData> listRawDataByContentId(MemoryId id, String contentId) {
        return listRawData(id).stream()
                .filter(rawData -> Objects.equals(rawData.contentId(), contentId))
                .toList();
    }

    default List<MemoryRawData> getRawDataByCaptionVectorIds(
            MemoryId id, Collection<String> captionVectorIds) {
        if (captionVectorIds == null || captionVectorIds.isEmpty()) {
            return List.of();
        }
        Set<String> requested =
                captionVectorIds.stream()
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());
        if (requested.isEmpty()) {
            return List.of();
        }
        return listRawData(id).stream()
                .filter(rawData -> requested.contains(rawData.captionVectorId()))
                .toList();
    }

    List<MemoryRawData> listRawData(MemoryId id);

    List<MemoryRawData> pollRawDataWithoutVector(MemoryId id, int limit, Duration minAge);

    void updateRawDataVectorIds(
            MemoryId id, Map<String, String> vectorIds, Map<String, Object> metadataPatch);
}
