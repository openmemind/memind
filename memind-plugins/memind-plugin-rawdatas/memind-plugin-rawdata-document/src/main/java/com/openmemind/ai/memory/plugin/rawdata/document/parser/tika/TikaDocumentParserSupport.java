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

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.CompositeExternalParser;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.sax.BodyContentHandler;

class TikaDocumentParserSupport {

    private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("\n{3,}");
    private static final Set<Class<? extends Parser>> EXCLUDED_PARSER_TYPES =
            Set.of(CompositeExternalParser.class, TesseractOCRParser.class);

    private final DefaultDetector detector;
    private final AutoDetectParser parser;
    private final PdfPageTextExtractor pdfPageTextExtractor = new PdfPageTextExtractor();

    TikaDocumentParserSupport() {
        ServiceLoader serviceLoader =
                new ServiceLoader(
                        TikaDocumentParserSupport.class.getClassLoader(),
                        LoadErrorHandler.IGNORE,
                        InitializableProblemHandler.IGNORE,
                        false);
        MimeTypes mimeTypes = MimeTypes.getDefaultMimeTypes(serviceLoader.getLoader());
        this.detector = new DefaultDetector(mimeTypes, serviceLoader);
        this.parser =
                new AutoDetectParser(
                        detector,
                        new DefaultParser(
                                mimeTypes.getMediaTypeRegistry(),
                                serviceLoader,
                                EXCLUDED_PARSER_TYPES));
    }

    ParsedDocument parse(byte[] data, String fileName, String mimeType) throws Exception {
        Metadata detectionMetadata = new Metadata();
        if (fileName != null && !fileName.isBlank()) {
            detectionMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        if (mimeType != null && !mimeType.isBlank()) {
            detectionMetadata.set(Metadata.CONTENT_TYPE, mimeType);
        }

        MediaType detectedType = detector.detect(new ByteArrayInputStream(data), detectionMetadata);

        Metadata parseMetadata = new Metadata();
        if (fileName != null && !fileName.isBlank()) {
            parseMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        if (mimeType != null && !mimeType.isBlank()) {
            parseMetadata.set(Metadata.CONTENT_TYPE, mimeType);
        }

        var handler = new BodyContentHandler(-1);
        parser.parse(new ByteArrayInputStream(data), handler, parseMetadata, new ParseContext());
        String detectedMimeType = detectedType != null ? detectedType.toString() : mimeType;
        return new ParsedDocument(detectedMimeType, handler.toString(), parseMetadata);
    }

    CanonicalDocumentText canonicalize(byte[] data, ParsedDocument parsedDocument)
            throws Exception {
        if ("application/pdf".equals(parsedDocument.detectedMimeType())) {
            PdfPageTextExtractor.PageAwarePdfText pageAware = pdfPageTextExtractor.extract(data);
            if (pageAware.pageAware()) {
                return new CanonicalDocumentText(
                        pageAware.text(), true, pageAware.emittedPageCount());
            }
        }
        return new CanonicalDocumentText(normalizeBlockText(parsedDocument.text()), false, null);
    }

    static String normalizeBlockText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        return EXTRA_BLANK_LINES.matcher(normalized).replaceAll("\n\n");
    }

    String resolveTitle(Metadata metadata, String fileName) {
        String title = metadata.get(TikaCoreProperties.TITLE);
        if (title != null && !title.isBlank()) {
            return title;
        }
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String leafFileName = Path.of(fileName).getFileName().toString();
        int extensionIndex = leafFileName.lastIndexOf('.');
        return extensionIndex > 0 ? leafFileName.substring(0, extensionIndex) : leafFileName;
    }

    List<String> componentParserClassNames() {
        return parser.getAllComponentParsers().stream()
                .map(component -> component.getClass().getName())
                .toList();
    }

    record ParsedDocument(String detectedMimeType, String text, Metadata metadata) {}

    record CanonicalDocumentText(String text, boolean pageAware, Integer pageCount) {}
}
