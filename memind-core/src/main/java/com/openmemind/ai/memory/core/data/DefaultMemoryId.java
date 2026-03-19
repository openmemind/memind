package com.openmemind.ai.memory.core.data;

import java.util.Objects;

/**
 * Default memory identifier implementation
 *
 * @author starboyate
 */
public record DefaultMemoryId(String userId, String agentId) implements MemoryId {

    public DefaultMemoryId {
        Objects.requireNonNull(userId, "userId must not be null");
    }

    public static DefaultMemoryId of(String userId, String agentId) {
        return new DefaultMemoryId(userId, agentId);
    }

    /**
     * Whether this memory ID has an agent scope
     */
    public boolean hasAgent() {
        return agentId != null && !agentId.isBlank();
    }

    @Override
    public String toIdentifier() {
        return hasAgent() ? userId + ":" + agentId : userId;
    }

    @Override
    public String getAttribute(String key) {
        return switch (key) {
            case "userId" -> userId;
            case "agentId" -> agentId;
            default -> null;
        };
    }
}
