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
package com.openmemind.ai.memory.server.configuration;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptionsSanitizer;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.DefaultContentParserRegistry;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeFactory;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeUnavailableException;
import com.openmemind.ai.memory.server.service.config.MemoryOptionService;
import com.openmemind.ai.memory.server.service.config.MemoryOptionsCodec;
import com.openmemind.ai.memory.server.service.config.MemoryOptionsProjectionMapper;
import com.openmemind.ai.memory.server.service.config.ServerRuntimeConfigRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class MemindServerRuntimeConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(MemindServerRuntimeConfiguration.class);

    @Bean
    MemoryOptionsCodec memoryOptionsCodec(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new MemoryOptionsCodec(objectMapperProvider.getIfAvailable(JsonUtils::mapper));
    }

    @Bean
    MemoryOptionsProjectionMapper memoryOptionsProjectionMapper() {
        return new MemoryOptionsProjectionMapper();
    }

    @Bean
    MemoryRuntimeFactory memoryRuntimeFactory(
            ObjectProvider<StructuredChatClient> structuredChatClientProvider,
            ObjectProvider<MemoryStore> memoryStoreProvider,
            ObjectProvider<MemoryBuffer> memoryBufferProvider,
            ObjectProvider<MemoryVector> memoryVectorProvider,
            ObjectProvider<MemoryTextSearch> memoryTextSearch,
            ObjectProvider<Reranker> reranker,
            ObjectProvider<ContentParser> contentParserProvider,
            ObjectProvider<RawDataPlugin> rawDataPluginProvider,
            ObjectProvider<ResourceFetcher> resourceFetcherProvider,
            ObjectProvider<BubbleTrackerStore> bubbleTrackerStoreProvider,
            ObjectProvider<MemoryObserver> memoryObserverProvider) {
        return options -> {
            StructuredChatClient structuredChatClient =
                    requireRuntimeDependency(structuredChatClientProvider);
            MemoryStore memoryStore = requireRuntimeDependency(memoryStoreProvider);
            MemoryBuffer memoryBuffer = requireRuntimeDependency(memoryBufferProvider);
            MemoryVector memoryVector = requireRuntimeDependency(memoryVectorProvider);
            MemoryBuildOptions effectiveOptions =
                    new MemoryBuildOptionsSanitizer().sanitize(options, memoryStore).options();
            List<ContentParser> parsers = resolveContentParsers(contentParserProvider);
            ContentParserRegistry contentParserRegistry =
                    parsers.isEmpty() ? null : new DefaultContentParserRegistry(parsers);
            ResourceFetcher resourceFetcher = resourceFetcherProvider.getIfAvailable();
            BubbleTrackerStore bubbleTrackerStore = bubbleTrackerStoreProvider.getIfAvailable();
            MemoryObserver memoryObserver = memoryObserverProvider.getIfAvailable();
            var builder =
                    Memory.builder()
                            .chatClient(structuredChatClient)
                            .store(memoryStore)
                            .buffer(memoryBuffer)
                            .vector(memoryVector)
                            .options(effectiveOptions)
                            .externallyManaged(true);
            if (bubbleTrackerStore != null) {
                builder.bubbleTrackerStore(bubbleTrackerStore);
            }
            if (memoryObserver != null) {
                builder.memoryObserver(memoryObserver);
            }
            if (contentParserRegistry != null) {
                builder.contentParserRegistry(contentParserRegistry);
            }
            rawDataPluginProvider.orderedStream().forEach(builder::rawDataPlugin);
            if (resourceFetcher != null) {
                builder.resourceFetcher(resourceFetcher);
            }
            MemoryTextSearch textSearch = memoryTextSearch.getIfAvailable();
            if (textSearch != null) {
                builder.textSearch(textSearch);
            }
            Reranker rerankerBean = reranker.getIfAvailable();
            if (rerankerBean != null) {
                builder.reranker(rerankerBean);
            }
            return new MemoryRuntimeFactory.CreationResult(builder.build(), effectiveOptions);
        };
    }

    private static List<ContentParser> resolveContentParsers(
            ObjectProvider<ContentParser> contentParserProvider) {
        return List.copyOf(contentParserProvider.orderedStream().toList());
    }

    @Bean
    MemoryRuntimeManager memoryRuntimeManager() {
        return new MemoryRuntimeManager();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    ApplicationRunner memoryRuntimeInitializer(
            ServerRuntimeConfigRepository repository,
            MemoryOptionsCodec codec,
            MemoryRuntimeManager runtimeManager,
            MemoryRuntimeFactory runtimeFactory) {
        return arguments -> {
            InitialRuntimeState state = loadOrInitialize(repository, codec);
            try {
                MemoryRuntimeFactory.CreationResult created =
                        runtimeFactory.create(state.options());
                runtimeManager.swap(created.memory(), created.effectiveOptions(), state.version());
            } catch (MemoryRuntimeUnavailableException exception) {
                log.warn("Memory runtime is unavailable at startup: {}", exception.getMessage());
            }
        };
    }

    @Bean
    MemoryOptionService memoryOptionService(
            ServerRuntimeConfigRepository repository,
            MemoryOptionsProjectionMapper projectionMapper,
            MemoryOptionsCodec codec,
            MemoryRuntimeManager runtimeManager,
            ObjectProvider<MemoryRuntimeFactory> runtimeFactoryProvider) {
        return new MemoryOptionService(
                repository,
                projectionMapper,
                codec,
                runtimeManager,
                runtimeFactoryProvider.getIfAvailable());
    }

    private static InitialRuntimeState loadOrInitialize(
            ServerRuntimeConfigRepository repository, MemoryOptionsCodec codec) {
        return repository
                .findActive(MemoryOptionService.CONFIG_KEY)
                .map(
                        config ->
                                new InitialRuntimeState(
                                        config.getConfigVersion(),
                                        codec.read(config.getConfigJson())))
                .orElseGet(
                        () -> {
                            MemoryBuildOptions defaults = MemoryBuildOptions.defaults();
                            repository.insertInitial(
                                    MemoryOptionService.CONFIG_KEY, 1L, codec.write(defaults));
                            return new InitialRuntimeState(1L, defaults);
                        });
    }

    private static <T> T requireRuntimeDependency(ObjectProvider<T> provider) {
        T dependency = provider.getIfAvailable();
        if (dependency == null) {
            throw new MemoryRuntimeUnavailableException(
                    "Memory runtime dependencies are unavailable. Configure the required runtime"
                            + " beans.");
        }
        return dependency;
    }

    private record InitialRuntimeState(long version, MemoryBuildOptions options) {}
}
