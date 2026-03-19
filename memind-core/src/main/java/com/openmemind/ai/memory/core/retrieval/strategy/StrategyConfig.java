package com.openmemind.ai.memory.core.retrieval.strategy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Strategy-specific configuration (sealed interface)
 *
 * <p>Obtain specific strategy configuration parameters through pattern matching.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SimpleStrategyConfig.class, name = "simple"),
    @JsonSubTypes.Type(value = DeepStrategyConfig.class, name = "deep")
})
public sealed interface StrategyConfig permits SimpleStrategyConfig, DeepStrategyConfig {
    String strategyName();
}
