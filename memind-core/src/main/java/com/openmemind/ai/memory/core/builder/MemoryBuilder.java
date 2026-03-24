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

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;

/**
 * Builds a {@link Memory} instance from runtime components.
 */
public interface MemoryBuilder {

    MemoryBuilder chatClient(StructuredChatClient chatClient);

    MemoryBuilder store(MemoryStore store);

    MemoryBuilder textSearch(MemoryTextSearch textSearch);

    MemoryBuilder vector(MemoryVector vector);

    MemoryBuilder reranker(Reranker reranker);

    MemoryBuilder options(MemoryBuildOptions options);

    Memory build();
}
