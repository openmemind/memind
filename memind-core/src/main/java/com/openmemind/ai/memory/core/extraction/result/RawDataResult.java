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

import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import java.util.List;

/**
 * RawData processing result
 *
 * @param rawDataList Persisted RawData list
 * @param segments Parsed segment list
 * @param existed Whether it exists (idempotent hit)
 */
public record RawDataResult(
        List<MemoryRawData> rawDataList, List<ParsedSegment> segments, boolean existed) {

    public RawDataResult withSegments(List<ParsedSegment> adjustedSegments) {
        return new RawDataResult(rawDataList, List.copyOf(adjustedSegments), existed);
    }

    /**
     * Create an empty result
     */
    public static RawDataResult empty() {
        return new RawDataResult(List.of(), List.of(), false);
    }

    /**
     * Create an existing result (idempotent hit)
     */
    public static RawDataResult existing(MemoryRawData rawData) {
        return new RawDataResult(List.of(rawData), List.of(), true);
    }

    /**
     * Whether it is empty
     */
    public boolean isEmpty() {
        return segments.isEmpty();
    }
}
