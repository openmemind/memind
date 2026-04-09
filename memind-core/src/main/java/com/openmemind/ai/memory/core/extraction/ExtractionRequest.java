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

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ImageContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ToolCallContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Memory extraction request
 *
 * @param memoryId Memory identifier
 * @param content Raw content
 * @param contentType Content type identifier (e.g. ContentTypes.CONVERSATION)
 * @param metadata Metadata
 * @param config Extraction configuration
 */
public record ExtractionRequest(
        MemoryId memoryId,
        RawContent content,
        RawFileInput fileInput,
        RawUrlInput urlInput,
        String contentType,
        Map<String, Object> metadata,
        ExtractionConfig config) {

    public ExtractionRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        config = config == null ? ExtractionConfig.defaults() : config;
    }

    /**
     * Create conversation extraction request
     */
    public static ExtractionRequest conversation(MemoryId memoryId, ConversationContent content) {
        return new ExtractionRequest(
                memoryId,
                content,
                null,
                null,
                ContentTypes.CONVERSATION,
                Map.of(),
                ExtractionConfig.defaults());
    }

    /**
     * Create text extraction request
     */
    public static ExtractionRequest text(MemoryId memoryId, String text) {
        var content = new ConversationContent(java.util.List.of(Message.user(text)));
        return new ExtractionRequest(
                memoryId,
                content,
                null,
                null,
                ContentTypes.CONVERSATION,
                Map.of(),
                ExtractionConfig.defaults());
    }

    /**
     * Create extraction request from arbitrary {@link RawContent}.
     *
     * <p>The content type is derived from {@link RawContent#contentType()}.
     */
    public static ExtractionRequest of(MemoryId memoryId, RawContent content) {
        return new ExtractionRequest(
                memoryId,
                content,
                null,
                null,
                content.contentType(),
                Map.of(),
                ExtractionConfig.defaults());
    }

    /**
     * Create document extraction request with normalized multimodal metadata.
     */
    public static ExtractionRequest document(MemoryId memoryId, DocumentContent content) {
        return new ExtractionRequest(
                memoryId,
                content,
                null,
                null,
                ContentTypes.DOCUMENT,
                normalizeMultimodalMetadata(
                        content.metadata(), content.sourceUri(), content.mimeType()),
                ExtractionConfig.defaults());
    }

    /**
     * Create image extraction request with normalized multimodal metadata.
     */
    public static ExtractionRequest image(MemoryId memoryId, ImageContent content) {
        return new ExtractionRequest(
                memoryId,
                content,
                null,
                null,
                ContentTypes.IMAGE,
                normalizeMultimodalMetadata(
                        content.metadata(), content.sourceUri(), content.mimeType()),
                ExtractionConfig.defaults());
    }

    /**
     * Create audio extraction request with normalized multimodal metadata.
     */
    public static ExtractionRequest audio(MemoryId memoryId, AudioContent content) {
        return new ExtractionRequest(
                memoryId,
                content,
                null,
                null,
                ContentTypes.AUDIO,
                normalizeMultimodalMetadata(
                        content.metadata(), content.sourceUri(), content.mimeType()),
                ExtractionConfig.defaults());
    }

    /**
     * Create parser-backed raw-file extraction request.
     */
    public static ExtractionRequest file(
            MemoryId memoryId, String fileName, byte[] data, String mimeType) {
        return new ExtractionRequest(
                memoryId,
                null,
                new RawFileInput(fileName, data, mimeType),
                null,
                null,
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
                null,
                null,
                new RawUrlInput(sourceUrl, fileName, mimeType),
                null,
                Map.of(),
                ExtractionConfig.defaults());
    }

    /**
     * Create tool call extraction request
     */
    public static ExtractionRequest toolCall(MemoryId memoryId, ToolCallContent content) {
        return new ExtractionRequest(
                memoryId,
                content,
                null,
                null,
                ContentTypes.TOOL_CALL,
                Map.of(),
                ExtractionConfig.agentOnly());
    }

    /**
     * Modify configuration
     */
    public ExtractionRequest withConfig(ExtractionConfig config) {
        return new ExtractionRequest(
                memoryId, content, fileInput, urlInput, contentType, metadata, config);
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
        return new ExtractionRequest(
                memoryId,
                content,
                fileInput,
                urlInput,
                contentType,
                Map.copyOf(newMetadata),
                config);
    }

    static Map<String, Object> normalizeMultimodalMetadata(RawContent content) {
        if (content instanceof DocumentContent documentContent) {
            return normalizeMultimodalMetadata(
                    documentContent.metadata(),
                    documentContent.sourceUri(),
                    documentContent.mimeType());
        }
        if (content instanceof ImageContent imageContent) {
            return normalizeMultimodalMetadata(
                    imageContent.metadata(), imageContent.sourceUri(), imageContent.mimeType());
        }
        if (content instanceof AudioContent audioContent) {
            return normalizeMultimodalMetadata(
                    audioContent.metadata(), audioContent.sourceUri(), audioContent.mimeType());
        }
        return Map.of();
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
