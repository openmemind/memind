package com.openmemind.ai.memory.evaluation.adapter;

/**
 * Memory extraction mode: STREAMING processes one by one (LLM boundary detection), CHUNK skips boundary detection in batches by session
 *
 */
public enum AddMode {
    /** Stream processing one by one, LLM boundary detection */
    STREAMING,
    /** Batch by session, skip boundary detection */
    CHUNK
}
