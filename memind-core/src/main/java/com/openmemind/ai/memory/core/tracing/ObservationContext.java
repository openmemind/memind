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
package com.openmemind.ai.memory.core.tracing;

import java.util.Map;

/**
 * Observation context (immutable)
 *
 * @param spanName           span name (must come from {@link MemorySpanNames} finite enumeration)
 * @param requestAttributes  request phase attributes
 * @param resultExtractor    result attribute extractor (filled back after span completion)
 * @param <T>                operation return value type
 */
public record ObservationContext<T>(
        String spanName,
        Map<String, Object> requestAttributes,
        ResultAttributeExtractor<T> resultExtractor) {

    public static <T> ObservationContext<T> of(String spanName) {
        return new ObservationContext<>(spanName, Map.of(), ResultAttributeExtractor.none());
    }

    public static <T> ObservationContext<T> of(String spanName, Map<String, Object> attrs) {
        return new ObservationContext<>(spanName, attrs, ResultAttributeExtractor.none());
    }

    public ObservationContext<T> withResultExtractor(ResultAttributeExtractor<T> extractor) {
        return new ObservationContext<>(spanName, requestAttributes, extractor);
    }
}
