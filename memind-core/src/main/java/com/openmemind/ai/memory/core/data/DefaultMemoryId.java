/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
