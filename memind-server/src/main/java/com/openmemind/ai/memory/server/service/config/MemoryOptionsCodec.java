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
package com.openmemind.ai.memory.server.service.config;

import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.RetrievalOptions;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class MemoryOptionsCodec {

    private final ObjectMapper objectMapper;

    public MemoryOptionsCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.rebuild().build();
    }

    public String write(MemoryBuildOptions options) {
        try {
            return objectMapper.writeValueAsString(PersistedMemoryOptions.from(options));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize memory options", e);
        }
    }

    public MemoryBuildOptions read(String json) {
        try {
            return objectMapper.readValue(json, PersistedMemoryOptions.class).toOptions();
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize memory options", e);
        }
    }

    private record PersistedMemoryOptions(
            ExtractionOptions extraction, RetrievalOptions retrieval) {

        private static PersistedMemoryOptions from(MemoryBuildOptions options) {
            return new PersistedMemoryOptions(options.extraction(), options.retrieval());
        }

        private MemoryBuildOptions toOptions() {
            return MemoryBuildOptions.builder().extraction(extraction).retrieval(retrieval).build();
        }
    }
}
