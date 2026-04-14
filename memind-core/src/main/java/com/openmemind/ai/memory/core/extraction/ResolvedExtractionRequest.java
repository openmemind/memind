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
package com.openmemind.ai.memory.core.extraction;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.resource.ResourceRef;
import java.util.Map;
import java.util.Objects;

record ResolvedExtractionRequest(
        MemoryId memoryId,
        RawContent content,
        String contentType,
        Map<String, Object> metadata,
        ExtractionConfig config,
        ResourceRef cleanupRef) {

    ResolvedExtractionRequest {
        Objects.requireNonNull(memoryId, "memoryId is required");
        Objects.requireNonNull(content, "content is required");
        Objects.requireNonNull(contentType, "contentType is required");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        config = Objects.requireNonNull(config, "config is required");
    }
}
