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

import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NativeTextDocumentContentParserTest {

    @Test
    void parseMayRefineProfileAfterParseWithinSameGovernanceFamily() {
        DocumentContent content =
                (DocumentContent)
                        new NativeTextDocumentContentParser()
                                .parse(
                                        "# Title".getBytes(StandardCharsets.UTF_8),
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "guide.md",
                                                "text/markdown",
                                                7L,
                                                null))
                                .block();

        assertThat(content).isNotNull();
        assertThat(content.metadata())
                .containsEntry("parserId", "document-native-text")
                .containsEntry("contentProfile", "document.markdown");
    }

    @Test
    void parseHtmlExtractsVisibleTextAndDecodesEntities() {
        DocumentContent content =
                (DocumentContent)
                        new NativeTextDocumentContentParser()
                                .parse(
                                        """
                                        <html>
                                          <head>
                                            <style>.hidden { display:none; }</style>
                                            <script>console.log('ignore');</script>
                                          </head>
                                          <body>
                                            <p>Hello &amp; welcome</p>
                                            <div>Line&nbsp;2</div>
                                          </body>
                                        </html>
                                        """
                                                .getBytes(StandardCharsets.UTF_8),
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "page.html",
                                                "text/html",
                                                32L,
                                                null))
                                .block();

        assertThat(content).isNotNull();
        assertThat(content.parsedText()).isEqualTo("Hello & welcome\nLine 2");
        assertThat(content.metadata()).containsEntry("contentProfile", "document.html");
    }

    @Test
    void parseMalformedHtmlStillPreservesVisibleText() {
        DocumentContent content =
                (DocumentContent)
                        new NativeTextDocumentContentParser()
                                .parse(
                                        "<div>Hello <span>world".getBytes(StandardCharsets.UTF_8),
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "fragment.html",
                                                "text/html",
                                                17L,
                                                null))
                                .block();

        assertThat(content).isNotNull();
        assertThat(content.parsedText()).isEqualTo("Hello world");
    }

    @Test
    void parseCsvKeepsTabularTextUntouched() {
        DocumentContent content =
                (DocumentContent)
                        new NativeTextDocumentContentParser()
                                .parse(
                                        "name,age\nalice,30".getBytes(StandardCharsets.UTF_8),
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "table.csv",
                                                "text/csv",
                                                17L,
                                                null))
                                .block();

        assertThat(content).isNotNull();
        assertThat(content.parsedText()).isEqualTo("name,age\nalice,30");
        assertThat(content.metadata()).containsEntry("contentProfile", "document.text");
    }

    @Test
    void parseRejectsEmptyPayload() {
        assertThatThrownBy(
                        () ->
                                new NativeTextDocumentContentParser()
                                        .parse(
                                                new byte[0],
                                                new SourceDescriptor(
                                                        SourceKind.FILE,
                                                        "empty.txt",
                                                        "text/plain",
                                                        0L,
                                                        null))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }
}
