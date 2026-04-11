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
package com.openmemind.ai.memory.core.extraction.rawdata;

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RawContentProcessorRegistry {

    private final Map<Class<?>, RawContentProcessor<?>> processors;

    public RawContentProcessorRegistry(List<RawContentProcessor<?>> processors) {
        Map<Class<?>, RawContentProcessor<?>> resolved = new LinkedHashMap<>();
        for (RawContentProcessor<?> processor :
                Objects.requireNonNull(processors, "processors").stream()
                        .filter(Objects::nonNull)
                        .toList()) {
            Class<?> contentClass =
                    Objects.requireNonNull(processor.contentClass(), "processor.contentClass()");
            RawContentProcessor<?> previous = resolved.putIfAbsent(contentClass, processor);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate processor registration for " + contentClass.getName());
            }
        }
        this.processors = Map.copyOf(resolved);
    }

    @SuppressWarnings("unchecked")
    public <T extends RawContent> RawContentProcessor<T> resolve(T content) {
        Class<?> current = Objects.requireNonNull(content, "content").getClass();
        while (current != null && current != Object.class) {
            RawContentProcessor<?> processor = processors.get(current);
            if (processor != null) {
                return (RawContentProcessor<T>) processor;
            }
            current = current.getSuperclass();
        }
        throw new IllegalArgumentException(
                "No processor registered for: " + content.getClass().getName());
    }

    public boolean supports(Class<? extends RawContent> contentClass) {
        Class<?> current = Objects.requireNonNull(contentClass, "contentClass");
        while (current != null && current != Object.class) {
            if (processors.containsKey(current)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    public List<RawContentProcessor<?>> all() {
        return List.copyOf(processors.values());
    }
}
