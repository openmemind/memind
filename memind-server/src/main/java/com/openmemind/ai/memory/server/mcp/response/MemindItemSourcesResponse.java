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
package com.openmemind.ai.memory.server.mcp.response;

import java.util.List;
import java.util.Map;

public record MemindItemSourcesResponse(List<Source> sources, List<String> missingItemIds) {

    public MemindItemSourcesResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
        missingItemIds = missingItemIds == null ? List.of() : List.copyOf(missingItemIds);
    }

    public record Source(
            String itemId,
            String rawDataId,
            String rawDataType,
            String caption,
            String sourceClient,
            Map<String, Object> metadata,
            Map<String, Object> segment) {
        public Source {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            segment = segment == null ? null : Map.copyOf(segment);
        }
    }
}
