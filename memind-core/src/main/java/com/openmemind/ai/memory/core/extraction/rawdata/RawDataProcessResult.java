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
package com.openmemind.ai.memory.core.extraction.rawdata;

import com.openmemind.ai.memory.core.data.MemoryRawData;
import java.util.List;

/**
 * RawData layer processing result
 *
 * @param rawDataList Persisted RawData list (one record per segment)
 * @param segments Parsed segment list (for MemoryItem layer use)
 * @param existed Whether it exists (idempotent hit)
 */
public record RawDataProcessResult(
        List<MemoryRawData> rawDataList, List<ParsedSegment> segments, boolean existed) {

    /**
     * Create an existing result (idempotent hit)
     *
     * @param rawData Existing RawData
     * @return Processing result
     */
    public static RawDataProcessResult existing(MemoryRawData rawData) {
        return new RawDataProcessResult(List.of(rawData), List.of(), true);
    }
}
