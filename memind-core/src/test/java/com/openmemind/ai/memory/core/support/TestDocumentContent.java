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
package com.openmemind.ai.memory.core.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.BuiltinContentProfiles;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.utils.HashUtils;
import java.util.Map;

/**
 * Test-only document-like RawContent used to decouple core tests from plugin document types.
 */
public final class TestDocumentContent extends RawContent {

    public static final String TYPE = "DOCUMENT";

    private final String title;
    private final String mimeType;
    private final String parsedText;
    private final String sourceUri;
    private final ContentGovernanceType directGovernanceType;
    private final String directContentProfile;
    private final Map<String, Object> metadata;

    @JsonCreator
    public TestDocumentContent(
            @JsonProperty("title") String title,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("parsedText") String parsedText,
            @JsonProperty("sourceUri") String sourceUri,
            @JsonProperty("directGovernanceType") ContentGovernanceType directGovernanceType,
            @JsonProperty("directContentProfile") String directContentProfile,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.title = title;
        this.mimeType = mimeType;
        this.parsedText = parsedText == null ? "" : parsedText;
        this.sourceUri = sourceUri;
        this.directGovernanceType =
                directGovernanceType != null
                        ? directGovernanceType
                        : deriveGovernanceType(mimeType);
        this.directContentProfile =
                directContentProfile != null
                        ? directContentProfile
                        : deriveContentProfile(mimeType, this.directGovernanceType);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static TestDocumentContent of(String title, String mimeType, String parsedText) {
        return new TestDocumentContent(title, mimeType, parsedText, null, null, null, Map.of());
    }

    @Override
    public String contentType() {
        return TYPE;
    }

    @Override
    public String toContentString() {
        return parsedText;
    }

    @Override
    public String getContentId() {
        return HashUtils.sampledSha256(parsedText);
    }

    @Override
    public Map<String, Object> contentMetadata() {
        return metadata;
    }

    @Override
    public RawContent withMetadata(Map<String, Object> metadata) {
        return new TestDocumentContent(
                title,
                mimeType,
                parsedText,
                sourceUri,
                directGovernanceType,
                directContentProfile,
                metadata);
    }

    @Override
    public String mimeType() {
        return mimeType;
    }

    @Override
    public String sourceUri() {
        return sourceUri;
    }

    @Override
    public ContentGovernanceType directGovernanceType() {
        return directGovernanceType;
    }

    @Override
    public String directContentProfile() {
        return directContentProfile;
    }

    public String title() {
        return title;
    }

    public String parsedText() {
        return parsedText;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    private static ContentGovernanceType deriveGovernanceType(String mimeType) {
        if ("text/markdown".equals(mimeType)
                || "text/html".equals(mimeType)
                || "text/plain".equals(mimeType)
                || "text/csv".equals(mimeType)) {
            return ContentGovernanceType.DOCUMENT_TEXT_LIKE;
        }
        return mimeType != null && !mimeType.isBlank()
                ? ContentGovernanceType.DOCUMENT_BINARY
                : ContentGovernanceType.DOCUMENT_TEXT_LIKE;
    }

    private static String deriveContentProfile(
            String mimeType, ContentGovernanceType governanceType) {
        if ("text/markdown".equals(mimeType)) {
            return BuiltinContentProfiles.DOCUMENT_MARKDOWN;
        }
        if ("text/html".equals(mimeType)) {
            return BuiltinContentProfiles.DOCUMENT_HTML;
        }
        if ("text/plain".equals(mimeType) || "text/csv".equals(mimeType)) {
            return BuiltinContentProfiles.DOCUMENT_TEXT;
        }
        if (governanceType == ContentGovernanceType.DOCUMENT_BINARY) {
            return BuiltinContentProfiles.DOCUMENT_BINARY;
        }
        return BuiltinContentProfiles.DOCUMENT_TEXT;
    }
}
