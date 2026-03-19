package com.openmemind.ai.memory.core.retrieval.intent;

/**
 * Retrieval Intent
 *
 * <p>Determined by the intent router whether the current query needs to retrieve memory
 *
 */
public enum RetrievalIntent {

    /** Needs to retrieve memory */
    RETRIEVE,

    /** Skip retrieval (the query does not need memory assistance) */
    SKIP
}
