package com.openmemind.ai.memory.core.data;

public interface MemoryId {
    String toIdentifier();

    default String getAttribute(String key) {
        return null;
    }
}
