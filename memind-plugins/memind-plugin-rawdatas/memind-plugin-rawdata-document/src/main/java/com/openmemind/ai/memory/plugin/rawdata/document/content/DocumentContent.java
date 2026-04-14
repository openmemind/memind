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
package com.openmemind.ai.memory.plugin.rawdata.document.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.utils.HashUtils;
import com.openmemind.ai.memory.plugin.rawdata.document.DocumentSemantics;
import com.openmemind.ai.memory.plugin.rawdata.document.content.document.DocumentSection;
import java.util.List;
import java.util.Map;

/**
 * Parsed document raw content.
 */
public final class DocumentContent extends RawContent {

    public static final String TYPE = "DOCUMENT";

    private final String title;
    private final String mimeType;
    private final String parsedText;
    private final List<DocumentSection> sections;
    private final String sourceUri;
    private final Map<String, Object> metadata;

    @JsonCreator
    public DocumentContent(
            @JsonProperty("title") String title,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("parsedText") String parsedText,
            @JsonProperty("sections") List<DocumentSection> sections,
            @JsonProperty("sourceUri") String sourceUri,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.title = title;
        this.mimeType = mimeType;
        this.parsedText = parsedText;
        this.sections = sections == null ? List.of() : List.copyOf(sections);
        this.sourceUri = sourceUri;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static DocumentContent of(String title, String mimeType, String parsedText) {
        return new DocumentContent(title, mimeType, parsedText, List.of(), null, Map.of());
    }

    @Override
    public String contentType() {
        return TYPE;
    }

    @Override
    public String toContentString() {
        return parsedText == null ? "" : parsedText;
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
        return new DocumentContent(title, mimeType, parsedText, sections, sourceUri, metadata);
    }

    @Override
    public String directGovernanceType() {
        return DocumentSemantics.directGovernance(mimeType, !sections.isEmpty());
    }

    @Override
    public String directContentProfile() {
        return DocumentSemantics.directProfile(mimeType, !sections.isEmpty());
    }

    @JsonProperty("title")
    public String title() {
        return title;
    }

    @Override
    @JsonProperty("mimeType")
    public String mimeType() {
        return mimeType;
    }

    @JsonProperty("parsedText")
    public String parsedText() {
        return parsedText;
    }

    @JsonProperty("sections")
    public List<DocumentSection> sections() {
        return sections;
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
