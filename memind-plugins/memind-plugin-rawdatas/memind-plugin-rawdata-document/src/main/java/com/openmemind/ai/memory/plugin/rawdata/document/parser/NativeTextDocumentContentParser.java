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

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.plugin.rawdata.document.DocumentSemantics;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * Built-in parser for text-like document formats that do not require heavyweight dependencies.
 */
public final class NativeTextDocumentContentParser implements ContentParser {

    private static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of("text/plain", "text/markdown", "text/html", "text/csv");
    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(".txt", ".md", ".html", ".htm", ".csv");
    private static final HtmlTextExtractor HTML_TEXT_EXTRACTOR = new HtmlTextExtractor();

    @Override
    public String parserId() {
        return "document-native-text";
    }

    @Override
    public String contentType() {
        return DocumentContent.TYPE;
    }

    @Override
    public String contentProfile() {
        return DocumentSemantics.PROFILE_TEXT;
    }

    @Override
    public String governanceType() {
        return DocumentSemantics.GOVERNANCE_TEXT_LIKE;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public boolean supports(SourceDescriptor source) {
        if (source.mimeType() != null && supportedMimeTypes().contains(source.mimeType())) {
            return true;
        }
        return hasSupportedExtension(source.fileName());
    }

    @Override
    public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
        if (data == null || data.length == 0) {
            return Mono.error(new IllegalArgumentException("Document payload must not be empty"));
        }

        String resolvedMimeType = resolveMimeType(source);
        String profile = resolveProfile(resolvedMimeType);
        String parsedText =
                normalizeText(new String(data, StandardCharsets.UTF_8), resolvedMimeType);

        return Mono.just(
                new DocumentContent(
                        resolveTitle(source.fileName()),
                        resolvedMimeType,
                        parsedText,
                        List.of(),
                        source.sourceUrl(),
                        Map.of("parserId", parserId(), "contentProfile", profile)));
    }

    private static boolean hasSupportedExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lowerCase = fileName.toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lowerCase::endsWith);
    }

    private static String resolveMimeType(SourceDescriptor source) {
        if (source.mimeType() != null && SUPPORTED_MIME_TYPES.contains(source.mimeType())) {
            return source.mimeType();
        }
        String fileName = source.fileName();
        if (fileName == null || fileName.isBlank()) {
            return "text/plain";
        }
        String lowerCase = fileName.toLowerCase(Locale.ROOT);
        if (lowerCase.endsWith(".md")) {
            return "text/markdown";
        }
        if (lowerCase.endsWith(".html") || lowerCase.endsWith(".htm")) {
            return "text/html";
        }
        if (lowerCase.endsWith(".csv")) {
            return "text/csv";
        }
        return "text/plain";
    }

    private static String resolveProfile(String mimeType) {
        return switch (mimeType) {
            case "text/markdown" -> DocumentSemantics.PROFILE_MARKDOWN;
            case "text/html" -> DocumentSemantics.PROFILE_HTML;
            default -> DocumentSemantics.PROFILE_TEXT;
        };
    }

    private static String normalizeText(String rawText, String mimeType) {
        String normalized = rawText.replace("\r\n", "\n").replace('\r', '\n');
        if ("text/html".equals(mimeType)) {
            return HTML_TEXT_EXTRACTOR.extract(normalized);
        }
        return normalized.strip();
    }

    private static String resolveTitle(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String leafName = Path.of(fileName).getFileName().toString();
        int extensionIndex = leafName.lastIndexOf('.');
        return extensionIndex > 0 ? leafName.substring(0, extensionIndex) : leafName;
    }
}
