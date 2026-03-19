package com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public final class AudioBlock extends ContentBlock {

    private final Source source;

    /**
     * Creates a new audio block for JSON deserialization.
     *
     * @param source The audio source (URL or Base64)
     * @throws NullPointerException if source is null
     */
    @JsonCreator
    public AudioBlock(@JsonProperty("source") Source source) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
    }

    /**
     * Gets the source of this audio content.
     *
     * @return The audio source containing URL or Base64 data
     */
    public Source getSource() {
        return source;
    }

    /**
     * Creates a new builder for constructing AudioBlock instances.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing AudioBlock instances.
     */
    public static class Builder {

        private Source source;

        /**
         * Sets the source for the audio content.
         *
         * @param source The audio source (URL or Base64)
         * @return This builder for chaining
         */
        public Builder source(Source source) {
            this.source = source;
            return this;
        }

        /**
         * Builds a new AudioBlock with the configured source.
         *
         * @return A new AudioBlock instance
         * @throws NullPointerException if source is null
         */
        public AudioBlock build() {
            return new AudioBlock(source);
        }
    }
}
