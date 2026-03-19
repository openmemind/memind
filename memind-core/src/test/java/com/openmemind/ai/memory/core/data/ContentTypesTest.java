package com.openmemind.ai.memory.core.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ContentTypes constants")
class ContentTypesTest {

    @Test
    @DisplayName("CONVERSATION constant should equal 'CONVERSATION'")
    void conversationConstant() {
        assertThat(ContentTypes.CONVERSATION).isEqualTo("CONVERSATION");
    }

    @Test
    @DisplayName("TOOL_CALL constant should equal 'TOOL_CALL'")
    void toolCallConstant() {
        assertThat(ContentTypes.TOOL_CALL).isEqualTo("TOOL_CALL");
    }

    @Test
    @DisplayName("All constants should be uppercase")
    void constantsAreUppercase() {
        assertThat(ContentTypes.CONVERSATION).isEqualTo(ContentTypes.CONVERSATION.toUpperCase());
        assertThat(ContentTypes.TOOL_CALL).isEqualTo(ContentTypes.TOOL_CALL.toUpperCase());
    }
}
