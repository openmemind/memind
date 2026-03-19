package com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.utils.HashUtils;
import java.util.Objects;

public final class Base64Source extends Source {

    @JsonProperty("media_type")
    private final String mediaType;

    private final String data;

    /**
     * Creates a new Base64 source for JSON deserialization.
     *
     * @param mediaType The MIME type of the media content (e.g., "image/jpeg")
     * @param data The Base64-encoded media data
     * @throws NullPointerException if mediaType or data is null
     */
    @JsonCreator
    public Base64Source(
            @JsonProperty("media_type") String mediaType, @JsonProperty("data") String data) {
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType cannot be null");
        this.data = Objects.requireNonNull(data, "data cannot be null");
    }

    /**
     * Gets the MIME type of the media content.
     *
     * @return The media type as a string (e.g., "image/jpeg")
     */
    public String getMediaType() {
        return mediaType;
    }

    /**
     * Gets the Base64-encoded media data.
     *
     * @return The Base64 data string
     */
    public String getData() {
        return data;
    }

    @Override
    public String toIdentifier() {
        return "data:" + mediaType + ";base64," + data;
    }

    @Override
    public String getContentId() {
        return HashUtils.sampledSha256(data);
    }

    /**
     * Creates a new builder for constructing Base64Source instances.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing Base64Source instances.
     */
    public static class Builder {

        private String mediaType;

        private String data;

        /**
         * Sets the MIME type for the media content.
         *
         * @param mediaType The MIME type (e.g., "image/jpeg")
         * @return This builder for chaining
         */
        public Builder mediaType(String mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        /**
         * Sets the Base64-encoded media data.
         *
         * @param data The Base64 data string
         * @return This builder for chaining
         */
        public Builder data(String data) {
            this.data = data;
            return this;
        }

        /**
         * Builds a new Base64Source with the configured properties.
         *
         * @return A new Base64Source instance
         * @throws NullPointerException if mediaType or data is null
         */
        public Base64Source build() {
            return new Base64Source(mediaType, data);
        }
    }
}
