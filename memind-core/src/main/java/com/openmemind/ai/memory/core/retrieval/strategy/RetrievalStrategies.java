package com.openmemind.ai.memory.core.retrieval.strategy;

/**
 * Built-in strategy name constants
 *
 */
public final class RetrievalStrategies {

    private RetrievalStrategies() {}

    /** Deep retrieval: three-layer drilling + multi-query expansion */
    public static final String DEEP_RETRIEVAL = "deep_retrieval";

    /** Simple retrieval: zero LLM calls, pure algorithm */
    public static final String SIMPLE = "simple";
}
