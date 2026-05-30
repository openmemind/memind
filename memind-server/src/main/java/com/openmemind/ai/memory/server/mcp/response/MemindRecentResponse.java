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

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MemindRecentResponse(List<Entry> entries, String nextCursor) {

    public MemindRecentResponse {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record Entry(
            String kind,
            String id,
            String title,
            String text,
            String sourceClient,
            Instant occurredAt,
            Instant createdAt,
            Map<String, Object> metadata) {
        public Entry {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
