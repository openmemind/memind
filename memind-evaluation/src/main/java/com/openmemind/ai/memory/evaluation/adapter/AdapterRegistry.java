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
