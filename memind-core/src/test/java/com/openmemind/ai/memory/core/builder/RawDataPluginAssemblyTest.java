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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicy;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicyRegistry;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.resource.ContentCapability;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.DefaultContentParserRegistry;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.resource.SourceKind;
import com.openmemind.ai.memory.core.support.TestDocumentContent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

class RawDataPluginAssemblyTest {

    private static final String IMAGE_TYPE = "IMAGE";
    private static final String AUDIO_TYPE = "AUDIO";

    @Test
    void duplicatePluginIdFailsFast() {
        RawDataPlugin first = new TestPlugin("document-plugin", List.of(), List.of());
        RawDataPlugin second = new TestPlugin("document-plugin", List.of(), List.of());

        assertThatThrownBy(
                        () -> new DefaultMemoryBuilder().rawDataPlugin(first).rawDataPlugin(second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate pluginId");
    }

    @Test
    void assemblerBuildsDefaultRegistryFromPluginParsersWhenExplicitRegistryIsMissing() {
        var plugin =
                new TestPlugin("document-plugin", List.of(testParser("document-test")), List.of());
        var assembly =
                new MemoryExtractionAssembler()
                        .assemble(
                                MemoryAssemblersTest.context(
                                        MemoryBuildOptions.defaults(),
                                        null,
                                        null,
                                        List.of(plugin)));
        var extractor = (DefaultMemoryExtractor) assembly.pipeline();

        ContentParserRegistry registry =
                MemoryAssemblersTest.readField(
                        extractor, "contentParserRegistry", ContentParserRegistry.class);

        assertThat(registry).isInstanceOf(DefaultContentParserRegistry.class);
        assertThat(registry.capabilities())
                .extracting(ContentCapability::parserId)
                .contains("document-test");
    }

    @Test
    void assemblerBuildsDefaultRegistryFromPluginParsersAcrossMultipleModalities() {
        var plugin =
                new TestPlugin(
                        "multimodal-plugin",
                        List.of(
                                testParser(
                                        "image-vision",
                                        IMAGE_TYPE,
                                        "image.caption-ocr",
                                        Set.of("image/png"),
                                        Set.of(".png")),
                                testParser(
                                        "audio-transcription",
                                        AUDIO_TYPE,
                                        "audio.transcript",
                                        Set.of("audio/mpeg"),
                                        Set.of(".mp3"))),
                        List.of());
        var assembly =
                new MemoryExtractionAssembler()
                        .assemble(
                                MemoryAssemblersTest.context(
                                        MemoryBuildOptions.defaults(),
                                        null,
                                        null,
                                        List.of(plugin)));
        var extractor = (DefaultMemoryExtractor) assembly.pipeline();

        ContentParserRegistry registry =
                MemoryAssemblersTest.readField(
                        extractor, "contentParserRegistry", ContentParserRegistry.class);

        assertThat(registry).isInstanceOf(DefaultContentParserRegistry.class);
        assertThat(registry.capabilities())
                .extracting(
                        ContentCapability::parserId,
                        ContentCapability::contentType,
                        ContentCapability::contentProfile)
                .containsExactlyInAnyOrder(
                        tuple("image-vision", IMAGE_TYPE, "image.caption-ocr"),
                        tuple("audio-transcription", AUDIO_TYPE, "audio.transcript"));
    }

    @Test
    void explicitParserRegistryRemainsAuthoritativeOverPluginParsers() {
        ContentParserRegistry explicitRegistry = Mockito.mock(ContentParserRegistry.class);
        var plugin =
                new TestPlugin("document-plugin", List.of(testParser("document-test")), List.of());
        var assembly =
                new MemoryExtractionAssembler()
                        .assemble(
                                MemoryAssemblersTest.context(
                                        MemoryBuildOptions.defaults(),
                                        explicitRegistry,
                                        null,
                                        List.of(plugin)));
        var extractor = (DefaultMemoryExtractor) assembly.pipeline();

        assertThat(
                        MemoryAssemblersTest.readField(
                                extractor, "contentParserRegistry", ContentParserRegistry.class))
                .isSameAs(explicitRegistry);
    }

    @Test
    void assemblerCollectsPluginOwnedIngestionPolicies() {
        var plugin =
                new RawDataPlugin() {
                    @Override
                    public String pluginId() {
                        return "rawdata-test";
                    }

                    @Override
                    public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
                        return List.of();
                    }

                    @Override
                    public List<RawDataIngestionPolicy> ingestionPolicies() {
                        return List.of(
                                new RawDataIngestionPolicy(
                                        TestDocumentContent.TYPE,
                                        Set.of(ContentGovernanceType.DOCUMENT_BINARY),
                                        new SourceLimitOptions(128)));
                    }
                };
        var assembly =
                new MemoryExtractionAssembler()
                        .assemble(
                                MemoryAssemblersTest.context(
                                        MemoryBuildOptions.defaults(),
                                        null,
                                        null,
                                        List.of(plugin)));
        var extractor = (DefaultMemoryExtractor) assembly.pipeline();

        assertThat(
                        MemoryAssemblersTest.readField(
                                extractor,
                                "ingestionPolicyRegistry",
                                RawDataIngestionPolicyRegistry.class))
                .extracting(
                        registry ->
                                registry.resolve(
                                                new ContentCapability(
                                                        "document-test",
                                                        TestDocumentContent.TYPE,
                                                        "document.binary",
                                                        ContentGovernanceType.DOCUMENT_BINARY,
                                                        Set.of("application/pdf"),
                                                        Set.of(".pdf"),
                                                        10))
                                        .sourceLimit()
                                        .maxBytes())
                .isEqualTo(128L);
    }

    @Test
    void duplicatePluginParserIdsFailFastDuringAssembly() {
        var first = new TestPlugin("plugin-1", List.of(testParser("duplicate-parser")), List.of());
        var second = new TestPlugin("plugin-2", List.of(testParser("duplicate-parser")), List.of());

        assertThatThrownBy(
                        () ->
                                new MemoryExtractionAssembler()
                                        .assemble(
                                                MemoryAssemblersTest.context(
                                                        MemoryBuildOptions.defaults(),
                                                        null,
                                                        null,
                                                        List.of(first, second))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate-parser");
    }

    @Test
    void assemblerDoesNotReserveRemovedCoreBuiltinPluginId() {
        var formerlyBuiltin = new TestPlugin("core-builtin-rawdata", List.of(), List.of());

        assertThatCode(
                        () ->
                                new MemoryExtractionAssembler()
                                        .assemble(
                                                MemoryAssemblersTest.context(
                                                        MemoryBuildOptions.defaults(),
                                                        null,
                                                        null,
                                                        List.of(formerlyBuiltin))))
                .doesNotThrowAnyException();
    }

    @Test
    void duplicatePluginSubtypeNamesFailFastDuringAssembly() {
        var first =
                new TestPlugin(
                        "plugin-1",
                        List.of(),
                        List.of(() -> Map.of("plugin_test", PluginContent.class)));
        var second =
                new TestPlugin(
                        "plugin-2",
                        List.of(),
                        List.of(() -> Map.of("plugin_test", PluginContent.class)));

        assertThatThrownBy(
                        () ->
                                new MemoryExtractionAssembler()
                                        .assemble(
                                                MemoryAssemblersTest.context(
                                                        MemoryBuildOptions.defaults(),
                                                        null,
                                                        null,
                                                        List.of(first, second))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plugin_test");
    }

    @Test
    void duplicateUserPluginIdsStillFailFastDuringAssembly() {
        var first = new TestPlugin("user-plugin", List.of(), List.of());
        var second = new TestPlugin("user-plugin", List.of(), List.of());

        assertThatThrownBy(
                        () ->
                                new MemoryExtractionAssembler()
                                        .assemble(
                                                MemoryAssemblersTest.context(
                                                        MemoryBuildOptions.defaults(),
                                                        null,
                                                        null,
                                                        List.of(first, second))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user-plugin");
    }

    private static ContentParser testParser(String parserId) {
        return testParser(
                parserId,
                TestDocumentContent.TYPE,
                "document.binary",
                Set.of("application/pdf"),
                Set.of(".pdf"));
    }

    private static ContentParser testParser(
            String parserId,
            String contentType,
            String contentProfile,
            Set<String> supportedMimeTypes,
            Set<String> supportedExtensions) {
        return new ContentParser() {
            @Override
            public String parserId() {
                return parserId;
            }

            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public String contentProfile() {
                return contentProfile;
            }

            @Override
            public Set<String> supportedMimeTypes() {
                return supportedMimeTypes;
            }

            @Override
            public Set<String> supportedExtensions() {
                return supportedExtensions;
            }

            @Override
            public boolean supports(SourceDescriptor source) {
                return source.sourceKind() == SourceKind.FILE
                        && source.mimeType() != null
                        && supportedMimeTypes.contains(source.mimeType());
            }

            @Override
            public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                return Mono.error(
                        new UnsupportedOperationException("not needed for assembly test"));
            }
        };
    }

    private record TestPlugin(
            String pluginId,
            List<ContentParser> parsers,
            List<RawContentTypeRegistrar> typeRegistrars)
            implements RawDataPlugin {

        @Override
        public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
            return List.of();
        }

        @Override
        public List<ContentParser> parsers(RawDataPluginContext context) {
            return parsers;
        }

        @Override
        public List<RawContentTypeRegistrar> typeRegistrars() {
            return typeRegistrars;
        }
    }

    private static final class PluginContent extends RawContent {

        @Override
        public String contentType() {
            return "PLUGIN_CONTENT";
        }

        @Override
        public String toContentString() {
            return "";
        }

        @Override
        public String getContentId() {
            return "plugin";
        }
    }
}
