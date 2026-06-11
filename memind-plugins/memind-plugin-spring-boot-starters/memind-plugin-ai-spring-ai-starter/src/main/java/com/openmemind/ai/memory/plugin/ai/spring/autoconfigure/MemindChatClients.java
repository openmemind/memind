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
package com.openmemind.ai.memory.plugin.ai.spring.autoconfigure;

import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MemindChatClients {

    private final String defaultClientId;
    private final StructuredChatClient defaultClient;
    private final Map<String, StructuredChatClient> clients;
    private final Map<ChatClientSlot, String> slotClientIds;
    private final Map<ChatClientSlot, StructuredChatClient> slotClients;

    public MemindChatClients(
            String defaultClientId,
            StructuredChatClient defaultClient,
            Map<String, StructuredChatClient> clients,
            Map<ChatClientSlot, String> slotClientIds,
            Map<ChatClientSlot, StructuredChatClient> slotClients) {
        this.defaultClientId = requireText(defaultClientId, "defaultClientId");
        this.defaultClient = Objects.requireNonNull(defaultClient, "defaultClient");
        this.clients = Map.copyOf(Objects.requireNonNull(clients, "clients"));
        this.slotClientIds = Map.copyOf(Objects.requireNonNull(slotClientIds, "slotClientIds"));
        this.slotClients = Map.copyOf(Objects.requireNonNull(slotClients, "slotClients"));
    }

    public String defaultClientId() {
        return defaultClientId;
    }

    public StructuredChatClient defaultClient() {
        return defaultClient;
    }

    public Map<String, StructuredChatClient> clients() {
        return clients;
    }

    public Set<String> clientIds() {
        return clients.keySet();
    }

    public Map<ChatClientSlot, String> slotClientIds() {
        return slotClientIds;
    }

    public Map<ChatClientSlot, StructuredChatClient> slotClients() {
        return slotClients;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
