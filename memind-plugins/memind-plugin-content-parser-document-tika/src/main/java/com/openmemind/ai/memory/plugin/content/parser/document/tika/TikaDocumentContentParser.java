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

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.resource.ContentParser;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

public final class TikaDocumentContentParser implements ContentParser {

    private static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of(
                    "text/plain",
                    "text/markdown",
                    "text/html",
                    "text/csv",
                    "application/pdf",
                    "application/rtf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final Set<String> WEAK_EXTENSIONS =
            Set.of(".txt", ".md", ".html", ".htm", ".csv", ".pdf", ".rtf", ".doc", ".docx");

    private final TikaDocumentParserSupport parserSupport = new TikaDocumentParserSupport();
    private final TikaDocumentMetadataMapper metadataMapper = new TikaDocumentMetadataMapper();

    @Override
    public String contentType() {
        return ContentTypes.DOCUMENT;
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    @Override
    public boolean supports(String fileName, String mimeType) {
        if (mimeType != null && SUPPORTED_MIME_TYPES.contains(mimeType)) {
            return true;
        }
        if (!"application/octet-stream".equals(mimeType) || fileName == null) {
            return false;
        }
        String lowerCaseFileName = fileName.toLowerCase(Locale.ROOT);
        return WEAK_EXTENSIONS.stream().anyMatch(lowerCaseFileName::endsWith);
    }

    @Override
    public Mono<RawContent> parse(byte[] data, String fileName, String mimeType) {
        if (data == null || data.length == 0) {
            return Mono.error(new IllegalArgumentException("Document payload must not be empty"));
        }
        return Mono.fromCallable(
                () -> {
                    var parsedDocument = parserSupport.parse(data, fileName, mimeType);
                    String normalizedText = parserSupport.normalizeText(parsedDocument.text());
                    Map<String, Object> metadata =
                            metadataMapper.map(
                                    parsedDocument.metadata(), parsedDocument.detectedMimeType());
                    if (normalizedText.isBlank() && !hasMeaningfulMetadata(metadata)) {
                        throw new IllegalStateException("Tika produced no text or metadata");
                    }
                    return (RawContent)
                            new DocumentContent(
                                    parserSupport.resolveTitle(parsedDocument.metadata(), fileName),
                                    resolvedMimeType(parsedDocument.detectedMimeType(), mimeType),
                                    normalizedText,
                                    List.of(),
                                    null,
                                    metadata);
                });
    }

    private static boolean hasMeaningfulMetadata(Map<String, Object> metadata) {
        return metadata.keySet().stream()
                .anyMatch(key -> !"parser".equals(key) && !"detectedMimeType".equals(key));
    }

    private static String resolvedMimeType(String detectedMimeType, String fallbackMimeType) {
        if (detectedMimeType != null && !detectedMimeType.isBlank()) {
            return detectedMimeType;
        }
        return fallbackMimeType;
    }
}
