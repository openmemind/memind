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
package com.openmemind.ai.memory.plugin.rawdata.document.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.resource.SourceKind;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
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
    void parseMarkdownPreservesAuthoredMarkdownSyntax() {
        DocumentContent content =
                (DocumentContent)
                        new NativeTextDocumentContentParser()
                                .parse(
                                        """
                                        # Guide

                                        - keep this list

                                        ```text
                                        code fence
                                        ```
                                        """
                                                .getBytes(StandardCharsets.UTF_8),
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "guide.md",
                                                "text/markdown",
                                                64L,
                                                null))
                                .block();

        assertThat(content).isNotNull();
        assertThat(content.parsedText())
                .isEqualTo(
                        String.join(
                                "\n",
                                "# Guide",
                                "",
                                "- keep this list",
                                "",
                                "```text",
                                "code fence",
                                "```"));
        assertThat(content.metadata()).containsEntry("contentProfile", "document.markdown");
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
        assertThat(content.parsedText()).isEqualTo("Hello & welcome\n\nLine 2");
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
    void parseCsvShapesRowsAndPublishesCsvProfile() {
        DocumentContent content =
                (DocumentContent)
                        new NativeTextDocumentContentParser()
                                .parse(
                                        "name,team\nAlice,Core\nBob,AI\n"
                                                .getBytes(StandardCharsets.UTF_8),
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "people.csv",
                                                "text/csv",
                                                27L,
                                                null))
                                .block();

        assertThat(content).isNotNull();
        assertThat(content.parsedText())
                .isEqualTo(
                        String.join(
                                "\n",
                                "Row 1:",
                                "name: Alice, team: Core",
                                "",
                                "Row 2:",
                                "name: Bob, team: AI"));
        assertThat(content.metadata()).containsEntry("contentProfile", "document.csv");
    }

    @Test
    void parseLowercaseHeaderCsvStillUsesHeaderNames() {
        DocumentContent content =
                (DocumentContent)
                        new NativeTextDocumentContentParser()
                                .parse(
                                        "name,team\nalice,core\nbob,ai\n"
                                                .getBytes(StandardCharsets.UTF_8),
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "people.csv",
                                                "text/csv",
                                                29L,
                                                null))
                                .block();

        assertThat(content).isNotNull();
        assertThat(content.parsedText())
                .isEqualTo(
                        String.join(
                                "\n",
                                "Row 1:",
                                "name: alice, team: core",
                                "",
                                "Row 2:",
                                "name: bob, team: ai"));
        assertThat(content.metadata()).containsEntry("contentProfile", "document.csv");
    }

    @Test
    void parseHeaderlessCsvUsesSyntheticKeys() {
        DocumentContent content =
                (DocumentContent)
                        new NativeTextDocumentContentParser()
                                .parse(
                                        "Alice,PM\nBob,Engineer\n".getBytes(StandardCharsets.UTF_8),
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "people.csv",
                                                "text/csv",
                                                24L,
                                                null))
                                .block();

        assertThat(content).isNotNull();
        assertThat(content.parsedText())
                .isEqualTo(
                        String.join(
                                "\n",
                                "Row 1:",
                                "column1: Alice, column2: PM",
                                "",
                                "Row 2:",
                                "column1: Bob, column2: Engineer"));
        assertThat(content.metadata()).containsEntry("contentProfile", "document.csv");
    }

    @Test
    void parseSameFixtureTwiceProducesStableTextAndContentId() {
        NativeTextDocumentContentParser parser = new NativeTextDocumentContentParser();
        SourceDescriptor source =
                new SourceDescriptor(SourceKind.FILE, "page.html", "text/html", 32L, null);
        byte[] payload = "<h1>Title</h1><p>Hello</p>".getBytes(StandardCharsets.UTF_8);

        DocumentContent first = (DocumentContent) parser.parse(payload, source).block();
        DocumentContent second = (DocumentContent) parser.parse(payload, source).block();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.parsedText()).isEqualTo(second.parsedText());
        assertThat(first.getContentId()).isEqualTo(second.getContentId());
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
