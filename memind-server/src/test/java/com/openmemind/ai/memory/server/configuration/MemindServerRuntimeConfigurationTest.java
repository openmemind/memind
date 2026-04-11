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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.DefaultMemory;
import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.llm.rerank.NoopReranker;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.resource.ContentCapability;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeFactory;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MemindServerRuntimeConfigurationTest {

    @Test
    void runtimeFactoryCombinesBuiltInAndPluginDocumentParsers() {
        var parser =
                new ContentParser() {
                    @Override
                    public String parserId() {
                        return "document-tika";
                    }

                    @Override
                    public String contentType() {
                        return ContentTypes.DOCUMENT;
                    }

                    @Override
                    public String contentProfile() {
                        return "document.binary";
                    }

                    @Override
                    public int priority() {
                        return 50;
                    }

                    @Override
                    public Set<String> supportedMimeTypes() {
                        return Set.of("application/pdf");
                    }

                    @Override
                    public Set<String> supportedExtensions() {
                        return Set.of(".pdf");
                    }

                    @Override
                    public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                        return Mono.just(
                                new DocumentContent(
                                        "Report",
                                        source.mimeType(),
                                        "parsed",
                                        java.util.List.of(),
                                        source.sourceUrl(),
                                        Map.of(
                                                "parserId",
                                                parserId(),
                                                "contentProfile",
                                                contentProfile())));
                    }
                };
        ResourceFetcher fetcher = request -> Mono.empty();
        var configuration = new MemindServerRuntimeConfiguration();

        MemoryRuntimeFactory factory =
                configuration.memoryRuntimeFactory(
                        provider(StructuredChatClient.class, proxy(StructuredChatClient.class)),
                        provider(MemoryStore.class, memoryStore()),
                        provider(MemoryBuffer.class, memoryBuffer()),
                        provider(MemoryVector.class, proxy(MemoryVector.class)),
                        emptyProvider(MemoryTextSearch.class),
                        provider(Reranker.class, new NoopReranker()),
                        provider(ContentParser.class, parser),
                        provider(ResourceFetcher.class, fetcher));

        Memory memory = factory.create(MemoryBuildOptions.defaults());
        var extractor = readField((DefaultMemory) memory, "extractor", MemoryExtractor.class);
        ContentParserRegistry registry =
                readField(extractor, "contentParserRegistry", ContentParserRegistry.class);

        assertThat(registry).isNotNull();
        assertThat(registry.capabilities())
                .extracting(ContentCapability::parserId)
                .contains("document-native-text", "document-tika");
        assertThat(readField(extractor, "resourceFetcher", ResourceFetcher.class))
                .isSameAs(fetcher);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        var beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("bean", bean);
        return beanFactory.getBeanProvider(type);
    }

    private static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        return new StaticListableBeanFactory().getBeanProvider(type);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (instance, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return switch (method.getName()) {
                                    case "toString" -> type.getSimpleName() + "Proxy";
                                    case "hashCode" -> System.identityHashCode(instance);
                                    case "equals" -> instance == args[0];
                                    default -> method.invoke(instance, args);
                                };
                            }
                            var returnType = method.getReturnType();
                            if (returnType == boolean.class) {
                                return false;
                            }
                            if (returnType == int.class) {
                                return 0;
                            }
                            if (returnType == long.class) {
                                return 0L;
                            }
                            if (returnType == Mono.class) {
                                return Mono.empty();
                            }
                            if (returnType == Flux.class) {
                                return Flux.empty();
                            }
                            if (returnType == java.util.List.class) {
                                return java.util.List.of();
                            }
                            if (returnType == java.util.Map.class) {
                                return Map.of();
                            }
                            return null;
                        });
    }

    private static MemoryStore memoryStore() {
        return MemoryStore.of(
                proxy(RawDataOperations.class),
                proxy(ItemOperations.class),
                proxy(InsightOperations.class));
    }

    private static MemoryBuffer memoryBuffer() {
        return MemoryBuffer.of(
                proxy(InsightBuffer.class),
                proxy(PendingConversationBuffer.class),
                proxy(RecentConversationBuffer.class));
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String fieldName, Class<T> fieldType) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) fieldType.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
