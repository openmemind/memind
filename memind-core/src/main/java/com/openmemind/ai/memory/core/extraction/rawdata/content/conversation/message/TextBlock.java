package com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class TextBlock extends ContentBlock {

    private final String text;

    /**
     * Creates a new text block for JSON deserialization.
     *
     * @param text The text content (null will be converted to empty string)
     */
    @JsonCreator
    private TextBlock(@JsonProperty("text") String text) {
        this.text = text != null ? text : "";
    }

    /**
     * Gets the text content of this block.
     *
     * @return The text content
     */
    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }

    /**
     * Creates a new builder for constructing TextBlock instances.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing TextBlock instances.
     */
    public static class Builder {

        private String text;

        /**
         * Sets the text content for the block.
         *
         * @param text The text content
         * @return This builder for chaining
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Builds a new TextBlock with the configured text.
         *
         * @return A new TextBlock instance (null text will be converted to empty string)
         */
        public TextBlock build() {
            return new TextBlock(text != null ? text : "");
        }
    }
}
