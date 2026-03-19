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
package com.openmemind.ai.memory.evaluation.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Memory adapter registry, automatically collects all MemoryAdapter implementations and indexes them by name
 *
 */
@Component
public class AdapterRegistry {
    private final Map<String, MemoryAdapter> adapters;

    public AdapterRegistry(List<MemoryAdapter> adapters) {
        this.adapters =
                adapters.stream()
                        .collect(Collectors.toMap(MemoryAdapter::name, Function.identity()));
    }

    public MemoryAdapter get(String name) {
        return Optional.ofNullable(adapters.get(name))
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unknown adapter: '"
                                                + name
                                                + "'. Available: "
                                                + adapters.keySet()));
    }
}
