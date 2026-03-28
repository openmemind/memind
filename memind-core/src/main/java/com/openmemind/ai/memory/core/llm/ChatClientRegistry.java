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
package com.openmemind.ai.memory.core.llm;

import java.util.Map;
import java.util.Objects;

/**
 * Resolves a {@link StructuredChatClient} for a given {@link ChatClientSlot}.
 *
 * <p>Each slot can be bound to a specific client. Unbound slots fall back to
 * the default client. Immutable after construction.
 */
public final class ChatClientRegistry {

    private final StructuredChatClient defaultClient;
    private final Map<ChatClientSlot, StructuredChatClient> slotClients;

    public ChatClientRegistry(
            StructuredChatClient defaultClient,
            Map<ChatClientSlot, StructuredChatClient> slotClients) {
        this.defaultClient = Objects.requireNonNull(defaultClient, "defaultClient");
        this.slotClients = Map.copyOf(Objects.requireNonNull(slotClients, "slotClients"));
    }

    public StructuredChatClient resolve(ChatClientSlot slot) {
        return slotClients.getOrDefault(slot, defaultClient);
    }

    public StructuredChatClient defaultClient() {
        return defaultClient;
    }
}
