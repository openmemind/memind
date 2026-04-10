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

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ContentParserRegistryTest {

    @Test
    void routesToSingleMatchingParser() {
        ContentParser documentParser =
                parser(
                        ContentTypes.DOCUMENT,
                        Set.of("application/pdf"),
                        "report.pdf",
                        DocumentContent.of("Report", "application/pdf", "hello pdf"));
        ContentParser imageParser =
                parser(
                        ContentTypes.IMAGE,
                        Set.of("image/png"),
                        "image.png",
                        new TestRawContent(ContentTypes.IMAGE, "pixel"));

        ContentParserRegistry registry =
                new DefaultContentParserRegistry(List.of(documentParser, imageParser));

        StepVerifier.create(registry.parse(new byte[] {1, 2, 3}, "report.pdf", "application/pdf"))
                .assertNext(
                        content -> {
                            assertThat(content.contentType()).isEqualTo(ContentTypes.DOCUMENT);
                            assertThat(content.toContentString()).isEqualTo("hello pdf");
                        })
                .verifyComplete();
    }

    @Test
    void rejectsDuplicateStableMimeClaimsAtConstruction() {
        ContentParser first =
                parser(
                        ContentTypes.DOCUMENT,
                        Set.of("application/pdf"),
                        "report.pdf",
                        DocumentContent.of("First", "application/pdf", "one"));
        ContentParser second =
                parser(
                        ContentTypes.IMAGE,
                        Set.of("application/pdf"),
                        "image.pdf",
                        new TestRawContent(ContentTypes.IMAGE, "two"));

        assertThatThrownBy(() -> new DefaultContentParserRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("application/pdf");
    }

    @Test
    void exposesCapabilitiesByContentType() {
        ContentParser documentParser =
                parser(
                        ContentTypes.DOCUMENT,
                        Set.of("application/pdf", "text/plain"),
                        "report.pdf",
                        DocumentContent.of("Report", "application/pdf", "hello"));

        ContentParserRegistry registry = new DefaultContentParserRegistry(List.of(documentParser));

        assertThat(registry.supportedMimeTypesByContentType())
                .isEqualTo(Map.of(ContentTypes.DOCUMENT, Set.of("application/pdf", "text/plain")));
    }

    @Test
    void failsWhenNoParserMatches() {
        ContentParserRegistry registry =
                new DefaultContentParserRegistry(
                        List.of(
                                parser(
                                        ContentTypes.DOCUMENT,
                                        Set.of("application/pdf"),
                                        "report.pdf",
                                        DocumentContent.of("Report", "application/pdf", "hello"))));

        StepVerifier.create(
                        registry.parse(new byte[] {9}, "unknown.bin", "application/octet-stream"))
                .verifyError(UnsupportedContentSourceException.class);
    }

    @Test
    void failsWhenMultipleParsersMatchAtRuntime() {
        ContentParser first =
                parser(
                        ContentTypes.DOCUMENT,
                        Set.of("application/pdf"),
                        "report.pdf",
                        DocumentContent.of("First", "application/pdf", "one"));
        ContentParser second =
                parserWithNoStableMime(
                        ContentTypes.IMAGE,
                        "report.pdf",
                        "application/pdf",
                        new TestRawContent(ContentTypes.IMAGE, "two"));

        ContentParserRegistry registry = new DefaultContentParserRegistry(List.of(first, second));

        StepVerifier.create(registry.parse(new byte[] {1}, "report.pdf", "application/pdf"))
                .verifyError(AmbiguousContentParserException.class);
    }

    private static ContentParser parser(
            String contentType,
            Set<String> mimeTypes,
            String supportedFileName,
            RawContent output) {
        return new ContentParser() {
            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public Set<String> supportedMimeTypes() {
                return mimeTypes;
            }

            @Override
            public boolean supports(String fileName, String mimeType) {
                return supportedMimeTypes().contains(mimeType)
                        && supportedFileName.equals(fileName);
            }

            @Override
            public Mono<RawContent> parse(byte[] data, String fileName, String mimeType) {
                return Mono.just(output);
            }
        };
    }

    private static ContentParser parserWithNoStableMime(
            String contentType,
            String supportedFileName,
            String supportedMimeType,
            RawContent output) {
        return new ContentParser() {
            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public Set<String> supportedMimeTypes() {
                return Set.of();
            }

            @Override
            public boolean supports(String fileName, String mimeType) {
                return supportedMimeType.equals(mimeType) && supportedFileName.equals(fileName);
            }

            @Override
            public Mono<RawContent> parse(byte[] data, String fileName, String mimeType) {
                return Mono.just(output);
            }
        };
    }

    private static final class TestRawContent extends RawContent {

        private final String contentType;
        private final String value;

        private TestRawContent(String contentType, String value) {
            this.contentType = contentType;
            this.value = value;
        }

        @Override
        public String contentType() {
            return contentType;
        }

        @Override
        public String toContentString() {
            return value;
        }

        @Override
        public String getContentId() {
            return value;
        }
    }
}
