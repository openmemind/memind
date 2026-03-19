package com.openmemind.ai.memory.core.data;

/**
 * Insight grouping strategy
 *
 * <p>sealed interface, referenced by {@link com.openmemind.ai.memory.core.data.enums.MemoryCategory},
 * determines how items within the same category are grouped to construct Insight.
 *
 */
public sealed interface GroupingStrategy {

    /** LLM semantic classification grouping */
    record LlmClassify() implements GroupingStrategy {}

    /** Group directly by metadata field value, zero LLM overhead */
    record MetadataField(String fieldName) implements GroupingStrategy {}
}
