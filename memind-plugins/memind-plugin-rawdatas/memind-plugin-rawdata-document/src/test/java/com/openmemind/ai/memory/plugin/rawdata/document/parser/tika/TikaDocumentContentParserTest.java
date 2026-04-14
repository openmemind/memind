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
package com.openmemind.ai.memory.plugin.rawdata.document.parser.tika;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.resource.SourceKind;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class TikaDocumentContentParserTest {

    private final TikaDocumentContentParser parser = new TikaDocumentContentParser();

    @Test
    void exposesBinaryDocumentCapability() {
        assertThat(parser.parserId()).isEqualTo("document-tika");
        assertThat(parser.contentProfile()).isEqualTo("document.binary");
        assertThat(parser.priority()).isEqualTo(50);
        assertThat(parser.supportedMimeTypes())
                .contains("application/pdf")
                .doesNotContain("text/plain", "text/markdown", "text/html", "text/csv");
        assertThat(
                        parser.supports(
                                new SourceDescriptor(
                                        SourceKind.FILE, "note.txt", "text/plain", 9L, null)))
                .isFalse();
    }

    @Test
    void parsesPdfWithPageMarkersAndCanonicalPageCount() {
        DocumentContent content =
                (DocumentContent)
                        parser.parse(
                                        twoPagePdf("First page", "Second page"),
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "report.pdf",
                                                "application/pdf",
                                                512L,
                                                null))
                                .block();

        assertThat(content.mimeType()).isEqualTo("application/pdf");
        assertThat(content.parsedText())
                .isEqualTo(
                        String.join("\n", "Page 1:", "First page", "", "Page 2:", "Second page"));
        assertThat(content.metadata())
                .containsEntry("parserId", "document-tika")
                .containsEntry("contentProfile", "document.pdf.tika")
                .containsEntry("pageCount", 2)
                .containsEntry("parser", "tika");
    }

    @Test
    void parsesDocxDocumentFromOctetStreamByExtension() {
        assertThat(
                        parser.supports(
                                new SourceDescriptor(
                                        SourceKind.FILE,
                                        "report.docx",
                                        "application/octet-stream",
                                        128L,
                                        null)))
                .isTrue();

        DocumentContent content =
                (DocumentContent)
                        parser.parse(
                                        minimalDocx("Hello DOCX"),
                                        new SourceDescriptor(
                                                SourceKind.FILE,
                                                "report.docx",
                                                "application/octet-stream",
                                                128L,
                                                null))
                                .block();

        assertThat(content.mimeType())
                .isEqualTo(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(content.parsedText()).contains("Hello DOCX");
        assertThat(content.title()).isNotBlank();
        assertThat(content.metadata())
                .containsEntry("parserId", "document-tika")
                .containsEntry("contentProfile", "document.binary");
    }

    @Test
    void rejectsEmptyPayload() {
        assertThatThrownBy(
                        () ->
                                parser.parse(
                                                new byte[0],
                                                new SourceDescriptor(
                                                        SourceKind.FILE,
                                                        "empty.pdf",
                                                        "application/pdf",
                                                        0L,
                                                        null))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsMetadataOnlyBinaryParseResults() {
        TikaDocumentParserSupport support =
                new TikaDocumentParserSupport() {
                    @Override
                    TikaDocumentParserSupport.ParsedDocument parse(
                            byte[] data, String fileName, String mimeType) {
                        return new TikaDocumentParserSupport.ParsedDocument(
                                "application/pdf", "", new org.apache.tika.metadata.Metadata());
                    }

                    @Override
                    TikaDocumentParserSupport.CanonicalDocumentText canonicalize(
                            byte[] data, TikaDocumentParserSupport.ParsedDocument parsedDocument) {
                        return new TikaDocumentParserSupport.CanonicalDocumentText("", false, null);
                    }
                };

        TikaDocumentMetadataMapper mapper =
                new TikaDocumentMetadataMapper() {
                    @Override
                    Map<String, Object> map(
                            org.apache.tika.metadata.Metadata metadata, String detectedMimeType) {
                        return Map.of("pageCount", 1);
                    }
                };

        TikaDocumentContentParser parser = new TikaDocumentContentParser(support, mapper);

        assertThatThrownBy(
                        () ->
                                parser.parse(
                                                "ignored".getBytes(StandardCharsets.UTF_8),
                                                new SourceDescriptor(
                                                        SourceKind.FILE,
                                                        "blank.pdf",
                                                        "application/pdf",
                                                        7L,
                                                        null))
                                        .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no text");
    }

    private static byte[] twoPagePdf(String firstPage, String secondPage) {
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            writePage(document, firstPage);
            writePage(document, secondPage);
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void writePage(org.apache.pdfbox.pdmodel.PDDocument document, String text)
            throws Exception {
        var page = new org.apache.pdfbox.pdmodel.PDPage();
        document.addPage(page);
        try (var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(
                    new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),
                    12);
            stream.newLineAtOffset(40, 700);
            stream.showText(text);
            stream.endText();
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
