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
package com.openmemind.ai.memory.core.data;

import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public record MemoryRawData(
        String id,
        String memoryId,

        /* Content type identifier (e.g. ConversationContent.TYPE) */
        String contentType,

        /* Source client that produced this raw data */
        String sourceClient,

        /* Original content ID */
        String contentId,

        /* Segmented content */
        Segment segment,

        /* One-sentence summary (for vector retrieval) */
        String caption,

        /* Caption ID in the vector database */
        String captionVectorId,

        /* Additional metadata */
        Map<String, Object> metadata,

        /* Logical reference to MemoryResource.id */
        String resourceId,

        /* MIME type of the source resource */
        String mimeType,

        /* Creation time */
        Instant createdAt,

        /* Timestamp of the first message in this segment */
        Instant startTime,

        /* Timestamp of the last message in this segment */
        Instant endTime) {

    public MemoryRawData {
        sourceClient = normalizeString(sourceClient);
        var normalizedMetadata = new LinkedHashMap<String, Object>();
        if (metadata != null) {
            normalizedMetadata.putAll(metadata);
        }
        if (resourceId != null && !normalizedMetadata.containsKey("resourceId")) {
            normalizedMetadata.put("resourceId", resourceId);
        }
        if (mimeType != null && !normalizedMetadata.containsKey("mimeType")) {
            normalizedMetadata.put("mimeType", mimeType);
        }
        metadata = Map.copyOf(normalizedMetadata);
        resourceId = projectString(metadata, "resourceId");
        mimeType = projectString(metadata, "mimeType");
    }

    public MemoryRawData(
            String id,
            String memoryId,
            String contentType,
            String contentId,
            Segment segment,
            String caption,
            String captionVectorId,
            Map<String, Object> metadata,
            String resourceId,
            String mimeType,
            Instant createdAt,
            Instant startTime,
            Instant endTime) {
        this(
                id,
                memoryId,
                contentType,
                null,
                contentId,
                segment,
                caption,
                captionVectorId,
                metadata,
                resourceId,
                mimeType,
                createdAt,
                startTime,
                endTime);
    }

    /**
     * Returns a new {@link MemoryRawData} instance, using the given vector ID, and merging patches in the metadata.
     * <p>Metadata merge rules: Based on the original {@code metadata}, overwrite/add the key-value pairs in {@code metadataPatch}.
     */
    public MemoryRawData withVectorId(String vectorId, Map<String, Object> metadataPatch) {
        Map<String, Object> merged = new HashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        if (metadataPatch != null) {
            merged.putAll(metadataPatch);
        }
        return new MemoryRawData(
                id,
                memoryId,
                contentType,
                sourceClient,
                contentId,
                segment,
                caption,
                vectorId,
                merged,
                null,
                null,
                createdAt,
                startTime,
                endTime);
    }

    /**
     * Returns a new {@link MemoryRawData} instance, replacing the metadata with the given {@code metadata}.
     */
    public MemoryRawData withMetadata(Map<String, Object> metadata) {
        return new MemoryRawData(
                id,
                memoryId,
                contentType,
                sourceClient,
                contentId,
                segment,
                caption,
                captionVectorId,
                metadata,
                null,
                null,
                createdAt,
                startTime,
                endTime);
    }

    private static String projectString(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static String normalizeString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
