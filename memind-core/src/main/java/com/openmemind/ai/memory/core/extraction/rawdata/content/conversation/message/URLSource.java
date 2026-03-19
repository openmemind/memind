package com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public final class URLSource extends Source {

    private final String url;

    /**
     * Creates a new URL source for JSON deserialization.
     *
     * @param url The URL pointing to the media content
     * @throws NullPointerException if url is null
     */
    @JsonCreator
    public URLSource(@JsonProperty("url") String url) {
        this.url = Objects.requireNonNull(url, "url cannot be null");
    }

    /**
     * Gets the URL that points to the media content.
     *
     * @return The URL as a string
     */
    public String getUrl() {
        return url;
    }

    @Override
    public String toIdentifier() {
        return url;
    }

    @Override
    public String getContentId() {
        return url;
    }

    /**
     * Creates a new builder for constructing URLSource instances.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing URLSource instances.
     */
    public static class Builder {

        private String url;

        /**
         * Sets the URL for the media content.
         *
         * @param url The URL pointing to the media content
         * @return This builder for chaining
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Builds a new URLSource with the configured URL.
         *
         * @return A new URLSource instance
         * @throws NullPointerException if url is null
         */
        public URLSource build() {
            return new URLSource(url);
        }
    }
}
