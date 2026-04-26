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
package com.openmemind.ai.memory.core.vector;

import java.util.Map;

/**
 * Immutable request for ordered vector batch search.
 */
public record VectorSearchRequest(
        String query, int topK, double minScore, Map<String, Object> filter) {

    public VectorSearchRequest {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        if (minScore < 0.0d || minScore > 1.0d) {
            throw new IllegalArgumentException("minScore must be in [0,1]");
        }
        filter = filter == null ? Map.of() : Map.copyOf(filter);
    }
}
