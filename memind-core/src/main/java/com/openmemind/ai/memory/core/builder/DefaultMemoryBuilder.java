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

import com.openmemind.ai.memory.core.DefaultMemory;
import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.llm.rerank.NoopReranker;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.stats.DefaultToolStatsService;
import com.openmemind.ai.memory.core.stats.ToolStatsService;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DefaultMemoryBuilder implements MemoryBuilder {

    private StructuredChatClient chatClient;
    private final Map<ChatClientSlot, StructuredChatClient> slotClients =
            new EnumMap<>(ChatClientSlot.class);
    private MemoryStore store;
    private MemoryBuffer buffer;
    private MemoryTextSearch textSearch;
    private MemoryVector vector;
    private Reranker reranker = new NoopReranker();
    private PromptRegistry promptRegistry = PromptRegistry.EMPTY;
    private MemoryBuildOptions options = MemoryBuildOptions.defaults();
    private boolean externallyManaged;

    @Override
    public MemoryBuilder chatClient(StructuredChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        return this;
    }

    @Override
    public MemoryBuilder chatClient(ChatClientSlot slot, StructuredChatClient chatClient) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(chatClient, "chatClient");
        slotClients.put(slot, chatClient);
        return this;
    }

    @Override
    public MemoryBuilder store(MemoryStore store) {
        this.store = Objects.requireNonNull(store, "store");
        return this;
    }

    @Override
    public MemoryBuilder buffer(MemoryBuffer buffer) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        return this;
    }

    @Override
    public MemoryBuilder textSearch(MemoryTextSearch textSearch) {
        this.textSearch = Objects.requireNonNull(textSearch, "textSearch");
        return this;
    }

    @Override
    public MemoryBuilder vector(MemoryVector vector) {
        this.vector = Objects.requireNonNull(vector, "vector");
        return this;
    }

    @Override
    public MemoryBuilder reranker(Reranker reranker) {
        this.reranker = Objects.requireNonNull(reranker, "reranker");
        return this;
    }

    @Override
    public MemoryBuilder promptRegistry(PromptRegistry promptRegistry) {
        this.promptRegistry = Objects.requireNonNull(promptRegistry, "promptRegistry");
        return this;
    }

    @Override
    public MemoryBuilder options(MemoryBuildOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        return this;
    }

    @Override
    public MemoryBuilder externallyManaged(boolean externallyManaged) {
        this.externallyManaged = externallyManaged;
        return this;
    }

    @Override
    public Memory build() {
        validateRequiredComponents();

        ChatClientRegistry registry = new ChatClientRegistry(chatClient, slotClients);
        MemoryAssemblyContext context =
                new MemoryAssemblyContext(
                        registry,
                        store,
                        buffer,
                        textSearch,
                        vector,
                        reranker,
                        promptRegistry,
                        options);
        MemoryExtractionAssembly extractionAssembly =
                new MemoryExtractionAssembler().assemble(context);
        var memoryRetriever = new MemoryRetrievalAssembler().assemble(context);
        ToolStatsService toolStatsService = new DefaultToolStatsService(context.memoryStore());
        AutoCloseable lifecycle =
                externallyManaged
                        ? lifecycle(extractionAssembly.lifecycle())
                        : lifecycle(
                                context.memoryVector(),
                                context.textSearch(),
                                context.chatClientRegistry().defaultClient(),
                                context.memoryStore(),
                                context.memoryBuffer(),
                                extractionAssembly.lifecycle());
        return new DefaultMemory(
                extractionAssembly.pipeline(),
                memoryRetriever,
                context.memoryStore(),
                context.memoryBuffer(),
                context.memoryVector(),
                toolStatsService,
                extractionAssembly.insightLayer(),
                lifecycle,
                options);
    }

    MemoryBuildOptions buildOptions() {
        return options;
    }

    private void validateRequiredComponents() {
        if (chatClient == null) {
            throw new IllegalStateException("Missing required chat client");
        }
        if (store == null) {
            throw new IllegalStateException("Missing required store");
        }
        if (buffer == null) {
            throw new IllegalStateException("Missing required buffer");
        }
        if (vector == null) {
            throw new IllegalStateException("Missing required vector");
        }
    }

    private AutoCloseable lifecycle(Object... candidates) {
        List<AutoCloseable> closeables = uniqueCloseables(candidates);

        return () -> {
            RuntimeException closeFailure = null;
            for (int i = closeables.size() - 1; i >= 0; i--) {
                try {
                    closeables.get(i).close();
                } catch (Exception e) {
                    if (closeFailure == null) {
                        closeFailure =
                                new IllegalStateException("Failed to close memory lifecycle", e);
                    } else {
                        closeFailure.addSuppressed(e);
                    }
                }
            }
            if (closeFailure != null) {
                throw closeFailure;
            }
        };
    }

    private static AutoCloseable autoCloseable(Object candidate) {
        return candidate instanceof AutoCloseable closeable ? closeable : null;
    }

    private static List<AutoCloseable> uniqueCloseables(Object... candidates) {
        List<AutoCloseable> ordered = new ArrayList<>();
        IdentityHashMap<AutoCloseable, Boolean> seen = new IdentityHashMap<>();
        for (Object candidate : candidates) {
            AutoCloseable closeable = autoCloseable(candidate);
            if (closeable != null && seen.put(closeable, Boolean.TRUE) == null) {
                ordered.add(closeable);
            }
        }
        return ordered;
    }
}
