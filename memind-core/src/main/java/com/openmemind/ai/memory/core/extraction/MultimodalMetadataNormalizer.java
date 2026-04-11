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
package com.openmemind.ai.memory.core.extraction;

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ImageContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.resource.ContentCapability;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Normalizes multimodal metadata so direct and parser-backed paths share the same keys.
 */
public final class MultimodalMetadataNormalizer {

    private MultimodalMetadataNormalizer() {}

    public static Map<String, Object> normalizeDirect(RawContent content) {
        return normalizeDirect(content, Map.of());
    }

    public static Map<String, Object> normalizeDirect(
            RawContent content, Map<String, Object> requestMetadata) {
        Objects.requireNonNull(content, "content");
        var normalized = new LinkedHashMap<String, Object>();
        putContentMetadata(normalized, contentMetadata(content));
        putRequestMetadata(normalized, requestMetadata);
        putTransportMetadata(normalized, content);

        ContentGovernanceType governanceType = deriveDirectGovernanceType(content);
        validateGovernanceType(normalized.get("governanceType"), governanceType);

        normalized.put("sourceKind", "DIRECT");
        putIfBlank(normalized, "parserId", "direct");
        normalized.put("governanceType", governanceType.name());
        normalized.put(
                "contentProfile",
                resolveContentProfile(
                        normalized.get("contentProfile"),
                        deriveDirectProfile(content),
                        governanceType));
        return Map.copyOf(normalized);
    }

    public static Map<String, Object> normalizeParsed(
            RawContent content, Map<String, Object> requestMetadata, ContentCapability capability) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(capability, "capability");

        var normalized = new LinkedHashMap<String, Object>();
        putContentMetadata(normalized, contentMetadata(content));
        if (requestMetadata != null) {
            normalized.putAll(requestMetadata);
        }
        putTransportMetadata(normalized, content);

        validateParserId(contentMetadata(content).get("parserId"), capability.parserId());
        validateGovernanceType(
                contentMetadata(content).get("governanceType"), capability.governanceType());

        normalized.put("parserId", capability.parserId());
        normalized.put("governanceType", capability.governanceType().name());
        normalized.put(
                "contentProfile",
                resolveContentProfile(
                        contentMetadata(content).get("contentProfile"),
                        capability.contentProfile(),
                        capability.governanceType()));
        return Map.copyOf(normalized);
    }

    public static RawContent normalizeDirectContent(RawContent content) {
        return normalizeDirectContent(content, Map.of());
    }

    public static RawContent normalizeDirectContent(
            RawContent content, Map<String, Object> requestMetadata) {
        return withMetadata(content, normalizeDirect(content, requestMetadata));
    }

    public static RawContent normalizeParsedContent(
            RawContent content, Map<String, Object> requestMetadata, ContentCapability capability) {
        return withMetadata(content, normalizeParsed(content, requestMetadata, capability));
    }

    public static RawContent withMetadata(RawContent content, Map<String, Object> metadata) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(metadata, "metadata");
        if (content instanceof DocumentContent documentContent) {
            return new DocumentContent(
                    documentContent.title(),
                    documentContent.mimeType(),
                    documentContent.parsedText(),
                    documentContent.sections(),
                    documentContent.sourceUri(),
                    metadata);
        }
        if (content instanceof ImageContent imageContent) {
            return new ImageContent(
                    imageContent.mimeType(),
                    imageContent.description(),
                    imageContent.ocrText(),
                    imageContent.sourceUri(),
                    metadata);
        }
        if (content instanceof AudioContent audioContent) {
            return new AudioContent(
                    audioContent.mimeType(),
                    audioContent.transcript(),
                    audioContent.segments(),
                    audioContent.sourceUri(),
                    metadata);
        }
        return content;
    }

    public static Map<String, Object> snapshot(RawContent content) {
        var normalized = new LinkedHashMap<String, Object>();
        putContentMetadata(normalized, contentMetadata(content));
        putTransportMetadata(normalized, content);
        return normalized;
    }

    private static Map<String, Object> contentMetadata(RawContent content) {
        if (content instanceof DocumentContent documentContent) {
            return documentContent.metadata();
        }
        if (content instanceof ImageContent imageContent) {
            return imageContent.metadata();
        }
        if (content instanceof AudioContent audioContent) {
            return audioContent.metadata();
        }
        return Map.of();
    }

    private static void putRequestMetadata(
            Map<String, Object> normalized, Map<String, Object> requestMetadata) {
        if (requestMetadata != null && !requestMetadata.isEmpty()) {
            normalized.putAll(requestMetadata);
        }
    }

    private static void putTransportMetadata(Map<String, Object> normalized, RawContent content) {
        if (content instanceof DocumentContent documentContent) {
            putIfNotBlank(normalized, "sourceUri", documentContent.sourceUri());
            putIfNotBlank(normalized, "mimeType", documentContent.mimeType());
        } else if (content instanceof ImageContent imageContent) {
            putIfNotBlank(normalized, "sourceUri", imageContent.sourceUri());
            putIfNotBlank(normalized, "mimeType", imageContent.mimeType());
        } else if (content instanceof AudioContent audioContent) {
            putIfNotBlank(normalized, "sourceUri", audioContent.sourceUri());
            putIfNotBlank(normalized, "mimeType", audioContent.mimeType());
        }
    }

    private static void putContentMetadata(
            Map<String, Object> normalized, Map<String, Object> contentMetadata) {
        if (contentMetadata != null) {
            normalized.putAll(contentMetadata);
        }
    }

    private static void putIfNotBlank(Map<String, Object> normalized, String key, String value) {
        if (value != null && !value.isBlank()) {
            normalized.put(key, value);
        }
    }

    private static void putIfBlank(Map<String, Object> normalized, String key, String value) {
        Object current = normalized.get(key);
        if (current == null || current.toString().isBlank()) {
            normalized.put(key, value);
        }
    }

    private static void validateParserId(Object parserId, String expectedParserId) {
        if (parserId == null || parserId.toString().isBlank()) {
            return;
        }
        if (!expectedParserId.equals(parserId.toString())) {
            throw new IllegalArgumentException(
                    "Parser metadata parserId=%s conflicts with authoritative parserId=%s"
                            .formatted(parserId, expectedParserId));
        }
    }

    private static void validateGovernanceType(
            Object governanceValue, ContentGovernanceType expectedGovernanceType) {
        if (governanceValue == null || governanceValue.toString().isBlank()) {
            return;
        }
        ContentGovernanceType actualGovernanceType =
                ContentGovernanceType.valueOf(governanceValue.toString());
        if (actualGovernanceType != expectedGovernanceType) {
            throw new IllegalArgumentException(
                    "Metadata governanceType=%s conflicts with authoritative governanceType=%s"
                            .formatted(actualGovernanceType, expectedGovernanceType));
        }
    }

    private static String resolveContentProfile(
            Object explicitValue, String defaultProfile, ContentGovernanceType governanceType) {
        String profile =
                explicitValue == null || explicitValue.toString().isBlank()
                        ? defaultProfile
                        : explicitValue.toString();
        BuiltinContentProfiles.governanceTypeOf(profile)
                .ifPresent(
                        builtinGovernanceType -> {
                            if (builtinGovernanceType != governanceType) {
                                throw new IllegalArgumentException(
                                        "Metadata contentProfile=%s conflicts with governanceType=%s"
                                                .formatted(profile, governanceType));
                            }
                        });
        return profile;
    }

    private static ContentGovernanceType deriveDirectGovernanceType(RawContent content) {
        if (content instanceof DocumentContent documentContent) {
            String mimeType = documentContent.mimeType();
            if ("text/markdown".equals(mimeType)
                    || "text/html".equals(mimeType)
                    || "text/plain".equals(mimeType)
                    || "text/csv".equals(mimeType)) {
                return ContentGovernanceType.DOCUMENT_TEXT_LIKE;
            }
            if (mimeType != null && !mimeType.isBlank()) {
                return ContentGovernanceType.DOCUMENT_BINARY;
            }
            return documentContent.sections().isEmpty()
                    ? ContentGovernanceType.DOCUMENT_TEXT_LIKE
                    : ContentGovernanceType.DOCUMENT_BINARY;
        }
        if (content instanceof ImageContent) {
            return ContentGovernanceType.IMAGE_CAPTION_OCR;
        }
        if (content instanceof AudioContent) {
            return ContentGovernanceType.AUDIO_TRANSCRIPT;
        }
        throw new IllegalArgumentException(
                "Unsupported multimodal content: " + content.contentType());
    }

    private static String deriveDirectProfile(RawContent content) {
        if (content instanceof DocumentContent documentContent) {
            String mimeType = documentContent.mimeType();
            if ("text/markdown".equals(mimeType)) {
                return BuiltinContentProfiles.DOCUMENT_MARKDOWN;
            }
            if ("text/html".equals(mimeType)) {
                return BuiltinContentProfiles.DOCUMENT_HTML;
            }
            if ("text/plain".equals(mimeType) || "text/csv".equals(mimeType)) {
                return BuiltinContentProfiles.DOCUMENT_TEXT;
            }
            if (mimeType != null && !mimeType.isBlank()) {
                return BuiltinContentProfiles.DOCUMENT_BINARY;
            }
            return documentContent.sections().isEmpty()
                    ? BuiltinContentProfiles.DOCUMENT_TEXT
                    : BuiltinContentProfiles.DOCUMENT_BINARY;
        }
        if (content instanceof ImageContent) {
            return BuiltinContentProfiles.IMAGE_CAPTION_OCR;
        }
        if (content instanceof AudioContent) {
            return BuiltinContentProfiles.AUDIO_TRANSCRIPT;
        }
        return content.contentType().toLowerCase(java.util.Locale.ROOT);
    }
}
