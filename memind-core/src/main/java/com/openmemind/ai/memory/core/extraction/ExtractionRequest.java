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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.source.DirectContentSource;
import com.openmemind.ai.memory.core.extraction.source.ExtractionSource;
import com.openmemind.ai.memory.core.extraction.source.FileExtractionSource;
import com.openmemind.ai.memory.core.extraction.source.UrlExtractionSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Memory extraction request
 *
 * @param memoryId Memory identifier
 * @param source Input source to resolve into extracted content
 * @param metadata Metadata
 * @param config Extraction configuration
 */
public record ExtractionRequest(
        MemoryId memoryId,
        ExtractionSource source,
        Map<String, Object> metadata,
        ExtractionConfig config) {

    public ExtractionRequest {
        memoryId = Objects.requireNonNull(memoryId, "memoryId is required");
        source = Objects.requireNonNull(source, "source is required");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        config = config == null ? ExtractionConfig.defaults() : config;
        if (source instanceof DirectContentSource directSource
                && directSource.content().directGovernanceType() != null) {
            RawContent normalizedContent =
                    MultimodalMetadataNormalizer.normalizeDirectContent(
                            directSource.content(), metadata);
            source = new DirectContentSource(normalizedContent);
            metadata = MultimodalMetadataNormalizer.snapshot(normalizedContent);
        }
    }

    /**
     * Create conversation extraction request
     */
    public static ExtractionRequest conversation(MemoryId memoryId, ConversationContent content) {
        return new ExtractionRequest(
                memoryId, new DirectContentSource(content), Map.of(), ExtractionConfig.defaults());
    }

    /**
     * Create text extraction request
     */
    public static ExtractionRequest text(MemoryId memoryId, String text) {
        var content = new ConversationContent(java.util.List.of(Message.user(text)));
        return new ExtractionRequest(
                memoryId, new DirectContentSource(content), Map.of(), ExtractionConfig.defaults());
    }

    /**
     * Create extraction request from arbitrary {@link RawContent}.
     */
    public static ExtractionRequest of(MemoryId memoryId, RawContent content) {
        return new ExtractionRequest(
                memoryId, new DirectContentSource(content), Map.of(), ExtractionConfig.defaults());
    }

    /**
     * Create parser-backed raw-file extraction request.
     */
    public static ExtractionRequest file(
            MemoryId memoryId, String fileName, byte[] data, String mimeType) {
        return new ExtractionRequest(
                memoryId,
                new FileExtractionSource(fileName, data, mimeType),
                Map.of(),
                ExtractionConfig.defaults());
    }

    /**
     * Create downloader-backed raw-url extraction request.
     */
    public static ExtractionRequest url(MemoryId memoryId, String sourceUrl) {
        return url(memoryId, sourceUrl, null, null);
    }

    /**
     * Create downloader-backed raw-url extraction request with optional overrides.
     */
    public static ExtractionRequest url(
            MemoryId memoryId, String sourceUrl, String fileName, String mimeType) {
        return new ExtractionRequest(
                memoryId,
                new UrlExtractionSource(sourceUrl, fileName, mimeType),
                Map.of(),
                ExtractionConfig.defaults());
    }

    /**
     * Modify configuration
     */
    public ExtractionRequest withConfig(ExtractionConfig config) {
        return new ExtractionRequest(memoryId, source, metadata, config);
    }

    /**
     * Disable Insight generation
     */
    public ExtractionRequest withoutInsight() {
        return withConfig(config.withEnableInsight(false));
    }

    /**
     * Add metadata
     */
    public ExtractionRequest withMetadata(String key, Object value) {
        var newMetadata = new java.util.HashMap<>(metadata);
        newMetadata.put(key, value);
        return new ExtractionRequest(memoryId, source, Map.copyOf(newMetadata), config);
    }

    /**
     * Returns direct content when this request is already backed by parsed {@link RawContent}.
     */
    public RawContent content() {
        return source instanceof DirectContentSource directSource ? directSource.content() : null;
    }

    static Map<String, Object> normalizeMultimodalMetadata(RawContent content) {
        return MultimodalMetadataNormalizer.snapshot(content);
    }

    static Map<String, Object> normalizeMultimodalMetadata(
            Map<String, Object> contentMetadata, String sourceUri, String mimeType) {
        var normalized = new LinkedHashMap<String, Object>();
        if (contentMetadata != null) {
            normalized.putAll(contentMetadata);
        }
        if (sourceUri != null && !sourceUri.isBlank()) {
            normalized.put("sourceUri", sourceUri);
        }
        if (mimeType != null && !mimeType.isBlank()) {
            normalized.put("mimeType", mimeType);
        }
        return Map.copyOf(normalized);
    }
}
