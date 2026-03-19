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
