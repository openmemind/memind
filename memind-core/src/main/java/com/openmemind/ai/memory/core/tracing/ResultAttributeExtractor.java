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
