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
package com.openmemind.ai.memory.plugin.rawdata.image.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.utils.HashUtils;
import com.openmemind.ai.memory.plugin.rawdata.image.ImageSemantics;
import java.util.Map;

/**
 * Parsed image raw content.
 */
public final class ImageContent extends RawContent {

    public static final String TYPE = "IMAGE";

    private final String mimeType;
    private final String description;
    private final String caption;
    private final String sourceUri;
    private final Map<String, Object> metadata;

    @JsonCreator
    public ImageContent(
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("description") String description,
            @JsonProperty("caption") String caption,
            @JsonProperty("sourceUri") String sourceUri,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.mimeType = mimeType;
        this.description = description;
        this.caption = caption;
        this.sourceUri = sourceUri;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ImageContent of(String description) {
        return new ImageContent(null, description, null, null, Map.of());
    }

    @Override
    public String contentType() {
        return TYPE;
    }

    @Override
    public String toContentString() {
        return description == null ? "" : description;
    }

    @Override
    public String getContentId() {
        return HashUtils.sampledSha256(toContentString());
    }

    @Override
    public Map<String, Object> contentMetadata() {
        return metadata;
    }

    @Override
    public RawContent withMetadata(Map<String, Object> metadata) {
        return new ImageContent(mimeType, description, caption, sourceUri, metadata);
    }

    @Override
    public String directGovernanceType() {
        return ImageSemantics.GOVERNANCE_CAPTION_OCR;
    }

    @Override
    public String directContentProfile() {
        return ImageSemantics.PROFILE_CAPTION_OCR;
    }

    @Override
    @JsonProperty("mimeType")
    public String mimeType() {
        return mimeType;
    }

    @JsonProperty("description")
    public String description() {
        return description;
    }

    @JsonProperty("caption")
    public String caption() {
        return caption;
    }

    @Override
    @JsonProperty("sourceUri")
    public String sourceUri() {
        return sourceUri;
    }

    @JsonProperty("metadata")
    public Map<String, Object> metadata() {
        return metadata;
    }
}
