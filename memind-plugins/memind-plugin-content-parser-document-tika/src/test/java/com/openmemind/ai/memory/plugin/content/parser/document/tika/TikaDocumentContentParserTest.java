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
package com.openmemind.ai.memory.plugin.content.parser.document.tika;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class TikaDocumentContentParserTest {

    private final TikaDocumentContentParser parser = new TikaDocumentContentParser();

    @Test
    void parsesPlainTextDocument() {
        DocumentContent content =
                (DocumentContent)
                        parser.parse(
                                        "hello text".getBytes(StandardCharsets.UTF_8),
                                        "note.txt",
                                        "text/plain")
                                .block();

        assertThat(content.mimeType()).isEqualTo("text/plain");
        assertThat(content.parsedText()).contains("hello text");
        assertThat(content.metadata()).containsEntry("parser", "tika");
    }

    @Test
    void parsesPdfDocument() {
        DocumentContent content =
                (DocumentContent)
                        parser.parse(minimalPdf(), "report.pdf", "application/pdf").block();

        assertThat(content.mimeType()).isEqualTo("application/pdf");
        assertThat(content.parsedText()).contains("Hello PDF");
    }

    @Test
    void parsesDocxDocumentFromOctetStreamByExtension() {
        assertThat(parser.supports("report.docx", "application/octet-stream")).isTrue();

        DocumentContent content =
                (DocumentContent)
                        parser.parse(
                                        minimalDocx("Hello DOCX"),
                                        "report.docx",
                                        "application/octet-stream")
                                .block();

        assertThat(content.mimeType())
                .isEqualTo(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(content.parsedText()).contains("Hello DOCX");
        assertThat(content.title()).isNotBlank();
    }

    @Test
    void rejectsEmptyPayload() {
        assertThatThrownBy(() -> parser.parse(new byte[0], "empty.txt", "text/plain").block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    private static byte[] minimalPdf() {
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            try (var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(
                        new org.apache.pdfbox.pdmodel.font.PDType1Font(
                                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),
                        12);
                stream.newLineAtOffset(40, 700);
                stream.showText("Hello PDF");
                stream.endText();
            }
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] minimalDocx(String text) {
        try {
            var out = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(out)) {
                write(
                        zip,
                        "[Content_Types].xml",
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Default Extension="xml" ContentType="application/xml"/>
                          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                        </Types>
                        """);
                write(
                        zip,
                        "_rels/.rels",
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                        </Relationships>
                        """);
                write(
                        zip,
                        "word/document.xml",
                        """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                          <w:body>
                            <w:p><w:r><w:t>\
                        """
                                + text
                                + """
                                    </w:t></w:r></w:p>
                                  </w:body>
                                </w:document>
                                """);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void write(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
