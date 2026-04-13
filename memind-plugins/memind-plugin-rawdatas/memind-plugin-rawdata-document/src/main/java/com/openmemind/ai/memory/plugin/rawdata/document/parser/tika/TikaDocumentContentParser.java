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

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.resource.UnsupportedContentSourceException;
import com.openmemind.ai.memory.plugin.rawdata.document.DocumentSemantics;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

public final class TikaDocumentContentParser implements ContentParser {

    private static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of(
                    "application/pdf",
                    "application/rtf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final Set<String> WEAK_EXTENSIONS = Set.of(".pdf", ".rtf", ".doc", ".docx");

    private final TikaDocumentParserSupport parserSupport = new TikaDocumentParserSupport();
    private final TikaDocumentMetadataMapper metadataMapper = new TikaDocumentMetadataMapper();

    @Override
    public String parserId() {
        return "document-tika";
    }

    @Override
    public String contentType() {
        return DocumentContent.TYPE;
    }

    @Override
    public String contentProfile() {
        return DocumentSemantics.PROFILE_BINARY;
    }

    @Override
    public String governanceType() {
        return DocumentSemantics.GOVERNANCE_BINARY;
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    @Override
    public Set<String> supportedExtensions() {
        return WEAK_EXTENSIONS;
    }

    @Override
    public boolean supports(SourceDescriptor source) {
        if (source.mimeType() != null && SUPPORTED_MIME_TYPES.contains(source.mimeType())) {
            return true;
        }
        String mimeType = source.mimeType();
        String fileName = source.fileName();
        if ((mimeType != null && !"application/octet-stream".equals(mimeType))
                || fileName == null) {
            return false;
        }
        String lowerCaseFileName = fileName.toLowerCase(Locale.ROOT);
        return WEAK_EXTENSIONS.stream().anyMatch(lowerCaseFileName::endsWith);
    }

    @Override
    public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
        if (data == null || data.length == 0) {
            return Mono.error(new IllegalArgumentException("Document payload must not be empty"));
        }
        if (!supports(source)) {
            return Mono.error(
                    new UnsupportedContentSourceException("Unsupported source: " + source));
        }
        return Mono.fromCallable(
                () -> {
                    var parsedDocument =
                            parserSupport.parse(data, source.fileName(), source.mimeType());
                    String normalizedText = parserSupport.normalizeText(parsedDocument.text());
                    Map<String, Object> metadata =
                            metadataMapper.map(
                                    parsedDocument.metadata(), parsedDocument.detectedMimeType());
                    var enrichedMetadata = new LinkedHashMap<>(metadata);
                    enrichedMetadata.put("parserId", parserId());
                    enrichedMetadata.put("contentProfile", contentProfile());
                    if (normalizedText.isBlank() && !hasMeaningfulMetadata(metadata)) {
                        throw new IllegalStateException("Tika produced no text or metadata");
                    }
                    return new DocumentContent(
                            parserSupport.resolveTitle(
                                    parsedDocument.metadata(), source.fileName()),
                            resolvedMimeType(parsedDocument.detectedMimeType(), source.mimeType()),
                            normalizedText,
                            List.of(),
                            source.sourceUrl(),
                            Map.copyOf(enrichedMetadata));
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
