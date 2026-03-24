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
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.stats.DefaultToolStatsService;
import com.openmemind.ai.memory.core.stats.ToolStatsService;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

public final class DefaultMemoryBuilder implements MemoryBuilder {

    private StructuredChatClient chatClient;
    private MemoryStore store;
    private MemoryTextSearch textSearch;
    private MemoryVector vector;
    private MemoryBuildOptions options = MemoryBuildOptions.defaults();

    @Override
    public MemoryBuilder chatClient(StructuredChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        return this;
    }

    @Override
    public MemoryBuilder store(MemoryStore store) {
        this.store = Objects.requireNonNull(store, "store");
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
    public MemoryBuilder options(MemoryBuildOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        return this;
    }

    @Override
    public Memory build() {
        validateRequiredComponents();

        MemoryAssemblyContext context =
                new MemoryAssemblyContext(chatClient, store, textSearch, vector, options);
        MemoryExtractionAssembly extractionAssembly =
                new MemoryExtractionAssembler().assemble(context);
        var memoryRetriever = new MemoryRetrievalAssembler().assemble(context);
        ToolStatsService toolStatsService = new DefaultToolStatsService(context.memoryStore());
        return new DefaultMemory(
                extractionAssembly.pipeline(),
                memoryRetriever,
                context.memoryStore(),
                context.memoryVector(),
                toolStatsService,
                extractionAssembly.insightLayer(),
                lifecycle(
                        extractionAssembly.lifecycle(),
                        context.memoryStore(),
                        context.textSearch(),
                        context.chatClient(),
                        context.memoryVector()));
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
        if (vector == null) {
            throw new IllegalStateException("Missing required vector");
        }
    }

    private AutoCloseable lifecycle(
            AutoCloseable extractionLifecycle,
            MemoryStore memoryStore,
            MemoryTextSearch memoryTextSearch,
            StructuredChatClient structuredChatClient,
            MemoryVector memoryVector) {
        List<AutoCloseable> closeables =
                uniqueCloseables(
                        extractionLifecycle,
                        autoCloseable(memoryVector),
                        autoCloseable(memoryTextSearch),
                        autoCloseable(structuredChatClient),
                        memoryStore);

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

    private static List<AutoCloseable> uniqueCloseables(AutoCloseable... closeables) {
        List<AutoCloseable> ordered = new ArrayList<>();
        IdentityHashMap<AutoCloseable, Boolean> seen = new IdentityHashMap<>();
        for (AutoCloseable closeable : closeables) {
            if (closeable != null && seen.put(closeable, Boolean.TRUE) == null) {
                ordered.add(closeable);
            }
        }
        return ordered;
    }
}
