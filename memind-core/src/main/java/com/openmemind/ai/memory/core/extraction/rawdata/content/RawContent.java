package com.openmemind.ai.memory.core.extraction.rawdata.content;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ConversationContent.class, name = "conversation"),
    @JsonSubTypes.Type(value = ToolCallContent.class, name = "tool_call")
})
public abstract class RawContent {

    public abstract String contentType();

    public abstract String toContentString();

    /**
     * Returns the unique identifier of the content
     *
     * <ul>
     *   <li>Text/Conversation type: SHA-256 (16 digit hex) based on content sampling
     *   <li>Media type (URL source): directly returns the URL
     *   <li>Media type (Base64 source): SHA-256 (16 digit hex) based on data sampling
     * </ul>
     */
    public abstract String getContentId();
}
