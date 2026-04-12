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
package com.openmemind.ai.memory.core.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.support.TestDocumentContent;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ContentParserRegistryTest {

    @Test
    void resolveReturnsHighestPriorityParserForSameContentType() {
        ContentParser nativeMarkdown =
                parser(
                        "document-native-text",
                        TestDocumentContent.TYPE,
                        "document.markdown",
                        100,
                        Set.of("text/markdown"),
                        Set.of(".md"));
        ContentParser fallbackDocument =
                parser(
                        "document-fallback",
                        TestDocumentContent.TYPE,
                        "document.binary",
                        10,
                        Set.of("text/markdown", "application/pdf"),
                        Set.of(".md", ".pdf"));

        ContentParserRegistry registry =
                new DefaultContentParserRegistry(List.of(fallbackDocument, nativeMarkdown));

        StepVerifier.create(
                        registry.resolve(
                                new SourceDescriptor(
                                        SourceKind.FILE, "notes.md", "text/markdown", 42L, null)))
                .assertNext(
                        resolution -> {
                            assertThat(resolution.parser().parserId())
                                    .isEqualTo("document-native-text");
                            assertThat(resolution.capability().contentProfile())
                                    .isEqualTo("document.markdown");
                            assertThat(resolution.capability().governanceType())
                                    .isEqualTo(ContentGovernanceType.DOCUMENT_TEXT_LIKE);
                        })
                .verifyComplete();
    }

    @Test
    void supportedCapabilitiesIncludeParserIdProfileAndExtensions() {
        ContentParserRegistry registry =
                new DefaultContentParserRegistry(
                        List.of(
                                parser(
                                        "document-native-text",
                                        TestDocumentContent.TYPE,
                                        "document.text",
                                        100,
                                        Set.of("text/plain"),
                                        Set.of(".txt"))));

        assertThat(registry.capabilities())
                .extracting(
                        ContentCapability::parserId,
                        ContentCapability::contentProfile,
                        ContentCapability::governanceType,
                        ContentCapability::supportedExtensions)
                .containsExactly(
                        tuple(
                                "document-native-text",
                                "document.text",
                                ContentGovernanceType.DOCUMENT_TEXT_LIKE,
                                Set.of(".txt")));
    }

    @Test
    void rejectsDuplicateParserIdAtConstruction() {
        ContentParser first =
                parser(
                        "document-parser",
                        TestDocumentContent.TYPE,
                        "document.text",
                        100,
                        Set.of("text/plain"),
                        Set.of(".txt"));
        ContentParser second =
                parser(
                        "document-parser",
                        TestDocumentContent.TYPE,
                        "document.markdown",
                        90,
                        Set.of("text/markdown"),
                        Set.of(".md"));

        assertThatThrownBy(() -> new DefaultContentParserRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("document-parser");
    }

    @Test
    void resolveFailsWhenTopParsersShareSamePriority() {
        ContentParser first =
                parser(
                        "document-markdown-a",
                        TestDocumentContent.TYPE,
                        "document.markdown",
                        100,
                        Set.of("text/markdown"),
                        Set.of(".md"));
        ContentParser second =
                parser(
                        "document-markdown-b",
                        TestDocumentContent.TYPE,
                        "document.markdown",
                        100,
                        Set.of("text/markdown"),
                        Set.of(".md"));

        ContentParserRegistry registry = new DefaultContentParserRegistry(List.of(first, second));

        StepVerifier.create(
                        registry.resolve(
                                new SourceDescriptor(
                                        SourceKind.FILE, "notes.md", "text/markdown", 42L, null)))
                .verifyError(AmbiguousContentParserException.class);
    }

    @Test
    void registryRejectsNonBuiltinProfileWithoutExplicitGovernanceType() {
        ContentParser parser =
                parser(
                        "document-custom",
                        TestDocumentContent.TYPE,
                        "document.pdf.tika",
                        100,
                        Set.of("application/pdf"),
                        Set.of(".pdf"));

        assertThatThrownBy(() -> new DefaultContentParserRegistry(List.of(parser)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("document.pdf.tika")
                .hasMessageContaining("governanceType()");
    }

    private static ContentParser parser(
            String parserId,
            String contentType,
            String contentProfile,
            int priority,
            Set<String> mimeTypes,
            Set<String> extensions) {
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
            public int priority() {
                return priority;
            }

            @Override
            public Set<String> supportedMimeTypes() {
                return mimeTypes;
            }

            @Override
            public Set<String> supportedExtensions() {
                return extensions;
            }

            @Override
            public boolean supports(SourceDescriptor source) {
                return source.mimeType() != null && supportedMimeTypes().contains(source.mimeType())
                        || source.fileName() != null
                                && supportedExtensions().stream()
                                        .anyMatch(ext -> source.fileName().endsWith(ext));
            }

            @Override
            public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                return Mono.just(
                        TestDocumentContent.of(
                                "Report", source.mimeType(), "hello " + contentProfile));
            }
        };
    }
}
