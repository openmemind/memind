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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RawDataOperationsTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-05-11T00:00:00Z");

    @Test
    void getRawDataByCaptionVectorIdsFiltersFallbackListAndKeepsStorageOrder() {
        var operations =
                new CountingRawDataOperations(
                        List.of(
                                rawData("rd-1", "vec-shared"),
                                rawData("rd-2", "vec-other"),
                                rawData("rd-3", "vec-shared"),
                                rawData("rd-4", null)));

        List<MemoryRawData> result =
                operations.getRawDataByCaptionVectorIds(
                        MEMORY_ID, List.of("vec-shared", "vec-missing"));

        assertThat(result).extracting(MemoryRawData::id).containsExactly("rd-1", "rd-3");
        assertThat(operations.listRawDataCalls).isEqualTo(1);
    }

    @Test
    void getRawDataByCaptionVectorIdsReturnsEmptyWithoutListingForEmptyInput() {
        var operations = new CountingRawDataOperations(List.of(rawData("rd-1", "vec-1")));

        assertThat(operations.getRawDataByCaptionVectorIds(MEMORY_ID, null)).isEmpty();
        assertThat(operations.getRawDataByCaptionVectorIds(MEMORY_ID, List.of())).isEmpty();
        assertThat(operations.getRawDataByCaptionVectorIds(MEMORY_ID, Arrays.asList(null, null)))
                .isEmpty();

        assertThat(operations.listRawDataCalls).isZero();
    }

    private static MemoryRawData rawData(String id, String captionVectorId) {
        return new MemoryRawData(
                id,
                MEMORY_ID.toIdentifier(),
                ConversationContent.TYPE,
                "content-" + id,
                new Segment("content " + id, "caption " + id, new CharBoundary(0, 7), Map.of()),
                "caption " + id,
                captionVectorId,
                Map.of(),
                null,
                null,
                NOW,
                NOW,
                NOW);
    }

    private static final class CountingRawDataOperations implements RawDataOperations {
        private final List<MemoryRawData> rows;
        private int listRawDataCalls;

        private CountingRawDataOperations(List<MemoryRawData> rows) {
            this.rows = rows;
        }

        @Override
        public void upsertRawData(MemoryId id, List<MemoryRawData> rawDataList) {}

        @Override
        public Optional<MemoryRawData> getRawData(MemoryId id, String rawDataId) {
            return Optional.empty();
        }

        @Override
        public Optional<MemoryRawData> getRawDataByContentId(MemoryId id, String contentId) {
            return Optional.empty();
        }

        @Override
        public List<MemoryRawData> listRawData(MemoryId id) {
            listRawDataCalls++;
            return rows;
        }

        @Override
        public List<MemoryRawData> pollRawDataWithoutVector(
                MemoryId id, int limit, Duration minAge) {
            return List.of();
        }

        @Override
        public void updateRawDataVectorIds(
                MemoryId id, Map<String, String> vectorIds, Map<String, Object> metadataPatch) {}
    }
}
