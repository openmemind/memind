package com.openmemind.ai.memory.core.rawdata.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig.ConversationSegmentStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConversationChunkingConfig Unit Test")
class ConversationChunkingConfigTest {

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName(
                "Single parameter constructor should use FIXED_SIZE strategy and default"
                        + " minMessages")
        void singleArgConstructorShouldUseDefaults() {
            var config = new ConversationChunkingConfig(5);

            assertThat(config.messagesPerChunk()).isEqualTo(5);
            assertThat(config.strategy()).isEqualTo(ConversationSegmentStrategy.FIXED_SIZE);
            assertThat(config.minMessagesPerSegment()).isEqualTo(20);
        }

        @Test
        @DisplayName("DEFAULT constant should use FIXED_SIZE strategy")
        void defaultConstantShouldUseFixedSizeStrategy() {
            assertThat(ConversationChunkingConfig.DEFAULT.messagesPerChunk()).isEqualTo(10);
            assertThat(ConversationChunkingConfig.DEFAULT.strategy())
                    .isEqualTo(ConversationSegmentStrategy.FIXED_SIZE);
            assertThat(ConversationChunkingConfig.DEFAULT.minMessagesPerSegment()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Full Constructor")
    class FullConstructorTests {

        @Test
        @DisplayName("Should support LLM strategy configuration")
        void shouldSupportLlmStrategy() {
            var config = new ConversationChunkingConfig(10, ConversationSegmentStrategy.LLM, 15);

            assertThat(config.strategy()).isEqualTo(ConversationSegmentStrategy.LLM);
            assertThat(config.minMessagesPerSegment()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("Parameter Validation")
    class ValidationTests {

        @Test
        @DisplayName("messagesPerChunk <= 0 should throw exception")
        void shouldRejectNonPositiveMessagesPerChunk() {
            assertThatThrownBy(() -> new ConversationChunkingConfig(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("strategy is null should throw exception")
        void shouldRejectNullStrategy() {
            assertThatThrownBy(() -> new ConversationChunkingConfig(10, null, 20))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("minMessagesPerSegment <= 0 should throw exception")
        void shouldRejectNonPositiveMinMessages() {
            assertThatThrownBy(
                            () ->
                                    new ConversationChunkingConfig(
                                            10, ConversationSegmentStrategy.LLM, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
