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

import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import java.util.Map;

/**
 * Parsed segment (including caption)
 *
 * @param text Segment text
 * @param caption Segment summary
 * @param startIndex Starting position in the original content
 * @param endIndex Ending position in the original content
 * @param rawDataId Persisted MemoryRawData business ID
 * @param metadata Segment metadata
 * @param runtimeContext Runtime-only extraction context
 */
public record ParsedSegment(
        String text,
        String caption,
        int startIndex,
        int endIndex,
        String rawDataId,
        Map<String, Object> metadata,
        SegmentRuntimeContext runtimeContext) {

    public ParsedSegment(
            String text,
            String caption,
            int startIndex,
            int endIndex,
            String rawDataId,
            Map<String, Object> metadata) {
        this(text, caption, startIndex, endIndex, rawDataId, metadata, null);
    }
}
