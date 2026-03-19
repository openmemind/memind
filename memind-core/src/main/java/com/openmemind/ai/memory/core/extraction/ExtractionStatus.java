package com.openmemind.ai.memory.core.extraction;

/**
 * Memory extraction status
 *
 */
public enum ExtractionStatus {
    /** All succeeded */
    SUCCESS,

    /** Partial success (some steps failed but there are some results) */
    PARTIAL_SUCCESS,

    /** All failed */
    FAILED
}
