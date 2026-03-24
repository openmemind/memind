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
package com.openmemind.ai.memory.core.builder;

import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.buffer.ConversationBuffer;
import com.openmemind.ai.memory.core.store.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.util.Objects;

record MemoryAssemblyContext(
        StructuredChatClient chatClient,
        MemoryStore memoryStore,
        MemoryTextSearch textSearch,
        MemoryVector memoryVector,
        MemoryBuildOptions options) {

    MemoryAssemblyContext {
        Objects.requireNonNull(chatClient, "chatClient");
        Objects.requireNonNull(memoryStore, "memoryStore");
        Objects.requireNonNull(memoryStore.rawDataOperations(), "memoryStore.rawDataOperations()");
        Objects.requireNonNull(memoryStore.itemOperations(), "memoryStore.itemOperations()");
        Objects.requireNonNull(memoryStore.insightOperations(), "memoryStore.insightOperations()");
        Objects.requireNonNull(
                memoryStore.insightBufferStore(), "memoryStore.insightBufferStore()");
        Objects.requireNonNull(
                memoryStore.conversationBufferStore(), "memoryStore.conversationBufferStore()");
        Objects.requireNonNull(memoryVector, "memoryVector");
        Objects.requireNonNull(options, "options");
    }

    InsightBuffer insightBufferStore() {
        return memoryStore.insightBufferStore();
    }

    ConversationBuffer conversationBufferStore() {
        return memoryStore.conversationBufferStore();
    }
}
