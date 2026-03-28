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

import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.util.Objects;

record MemoryAssemblyContext(
        ChatClientRegistry chatClientRegistry,
        MemoryStore memoryStore,
        MemoryBuffer memoryBuffer,
        MemoryTextSearch textSearch,
        MemoryVector memoryVector,
        Reranker reranker,
        MemoryBuildOptions options) {

    MemoryAssemblyContext {
        Objects.requireNonNull(chatClientRegistry, "chatClientRegistry");
        Objects.requireNonNull(memoryStore, "memoryStore");
        Objects.requireNonNull(memoryStore.rawDataOperations(), "memoryStore.rawDataOperations()");
        Objects.requireNonNull(memoryStore.itemOperations(), "memoryStore.itemOperations()");
        Objects.requireNonNull(memoryStore.insightOperations(), "memoryStore.insightOperations()");
        Objects.requireNonNull(memoryBuffer, "memoryBuffer");
        Objects.requireNonNull(memoryBuffer.insightBuffer(), "memoryBuffer.insightBuffer()");
        Objects.requireNonNull(
                memoryBuffer.pendingConversationBuffer(),
                "memoryBuffer.pendingConversationBuffer()");
        Objects.requireNonNull(
                memoryBuffer.recentConversationBuffer(), "memoryBuffer.recentConversationBuffer()");
        Objects.requireNonNull(memoryVector, "memoryVector");
        Objects.requireNonNull(reranker, "reranker");
        Objects.requireNonNull(options, "options");
    }

    InsightBuffer insightBuffer() {
        return memoryBuffer.insightBuffer();
    }

    PendingConversationBuffer pendingConversationBuffer() {
        return memoryBuffer.pendingConversationBuffer();
    }

    RecentConversationBuffer recentConversationBuffer() {
        return memoryBuffer.recentConversationBuffer();
    }
}
