package com.openmemind.ai.memory.core.tracing;

import java.util.Map;

/**
 * Result Attribute Extractor (Generic)
 *
 * <p>Extract attributes from the return value after the operation is completed and fill them back to the span.
 * The generic parameter ensures type safety in the decorator, eliminating the need for instanceof checks.
 *
 * @param <T> The type of the operation return value
 */
@FunctionalInterface
public interface ResultAttributeExtractor<T> {

    Map<String, Object> extract(T result);

    static <T> ResultAttributeExtractor<T> none() {
        return r -> Map.of();
    }
}
