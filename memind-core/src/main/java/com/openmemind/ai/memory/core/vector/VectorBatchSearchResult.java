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

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered result bundle returned from vector batch search.
 */
public record VectorBatchSearchResult(List<List<VectorSearchResult>> results, int invocationCount) {

    public VectorBatchSearchResult {
        if (results == null) {
            throw new IllegalArgumentException("results must not be null");
        }
        var copied = new ArrayList<List<VectorSearchResult>>(results.size());
        for (var inner : results) {
            if (inner == null) {
                throw new IllegalArgumentException("results must not contain null lists");
            }
            copied.add(List.copyOf(inner));
        }
        if (invocationCount < 0) {
            throw new IllegalArgumentException("invocationCount must be non-negative");
        }
        results = List.copyOf(copied);
    }
}
