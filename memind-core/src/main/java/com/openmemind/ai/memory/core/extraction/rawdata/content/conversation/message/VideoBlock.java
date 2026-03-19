package com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class VideoBlock extends ContentBlock {

    private final Source source;

    /**
     * Creates a new video block for JSON deserialization.
     *
     * @param source The video source (URL or Base64)
     */
    @JsonCreator
    public VideoBlock(@JsonProperty("source") Source source) {
        this.source = source;
    }

    /**
     * Gets the source of this video content.
     *
     * @return The video source containing URL or Base64 data
     */
    public Source getSource() {
        return source;
    }

    /**
     * Creates a new builder for constructing VideoBlock instances.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing VideoBlock instances.
     */
    public static class Builder {

        private Source source;

        /**
         * Sets the source for the video content.
         *
         * @param source The video source (URL or Base64)
         * @return This builder for chaining
         */
        public Builder source(Source source) {
            this.source = source;
            return this;
        }

        /**
         * Builds a new VideoBlock with the configured source.
         *
         * @return A new VideoBlock instance
         */
        public VideoBlock build() {
            return new VideoBlock(source);
        }
    }
}
