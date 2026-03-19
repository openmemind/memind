package com.openmemind.ai.memory.evaluation.adapter;

/**
 * Abstract base class of MemoryAdapter, providing common utility methods
 *
 */
public abstract class BaseMemoryAdapter implements MemoryAdapter {

    /**
     * Build userId for the memory system based on conversation ID and speaker name
     *
     * @param convId      Conversation ID
     * @param speakerName Speaker name (spaces replaced with underscores)
     * @return userId, format: {convId}_{speakerName}
     */
    public static String buildUserId(String convId, String speakerName) {
        return convId + "_" + speakerName.replace(" ", "_");
    }
}
