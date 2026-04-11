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
package com.openmemind.ai.memory.core.extraction.rawdata.content;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ConversationContent.class, name = "conversation"),
    @JsonSubTypes.Type(value = ToolCallContent.class, name = "tool_call"),
    @JsonSubTypes.Type(value = DocumentContent.class, name = "document"),
    @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
    @JsonSubTypes.Type(value = AudioContent.class, name = "audio")
})
public abstract class RawContent {

    public abstract String contentType();

    public abstract String toContentString();

    /** Returns the text fingerprint of the content payload. */
    @JsonIgnore
    public abstract String getContentId();

    public Map<String, Object> contentMetadata() {
        return Map.of();
    }

    public RawContent withMetadata(Map<String, Object> metadata) {
        throw new UnsupportedOperationException(
                getClass().getName() + " must override withMetadata(metadata)");
    }

    public String mimeType() {
        return null;
    }

    public String sourceUri() {
        return null;
    }

    public ContentGovernanceType directGovernanceType() {
        return null;
    }

    public String directContentProfile() {
        return null;
    }
}
