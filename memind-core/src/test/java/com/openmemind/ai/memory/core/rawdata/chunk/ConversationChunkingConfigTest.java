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
package com.openmemind.ai.memory.core.rawdata.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig.ConversationSegmentStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConversationChunkingConfig Unit Test")
class ConversationChunkingConfigTest {

    @Test
    @DisplayName("single parameter constructor retains backward compatible defaults")
    void singleArgumentConstructorRetainsBackCompatDefaults() {
        var config = new ConversationChunkingConfig(5);

        assertThat(config.messagesPerChunk()).isEqualTo(5);
        assertThat(config.strategy()).isEqualTo(ConversationSegmentStrategy.FIXED_SIZE);
        assertThat(config.minMessagesPerSegment()).isEqualTo(20);
    }

    @Test
    @DisplayName("default constant keeps fixed size behavior")
    void defaultConstantKeepsFixedSizeBehavior() {
        assertThat(ConversationChunkingConfig.DEFAULT.messagesPerChunk()).isEqualTo(10);
        assertThat(ConversationChunkingConfig.DEFAULT.strategy())
                .isEqualTo(ConversationSegmentStrategy.FIXED_SIZE);
        assertThat(ConversationChunkingConfig.DEFAULT.minMessagesPerSegment()).isEqualTo(20);
    }

    @Test
    @DisplayName("full constructor supports llm strategy configuration")
    void fullConstructorSupportsLlmStrategyConfiguration() {
        var config = new ConversationChunkingConfig(10, ConversationSegmentStrategy.LLM, 15);

        assertThat(config.strategy()).isEqualTo(ConversationSegmentStrategy.LLM);
        assertThat(config.minMessagesPerSegment()).isEqualTo(15);
    }

    @Test
    @DisplayName("rejects non positive messagesPerChunk")
    void rejectsNonPositiveMessagesPerChunk() {
        assertThatThrownBy(() -> new ConversationChunkingConfig(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messagesPerChunk");
    }

    @Test
    @DisplayName("rejects null strategy")
    void rejectsNullStrategy() {
        assertThatThrownBy(() -> new ConversationChunkingConfig(10, null, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strategy");
    }

    @Test
    @DisplayName("rejects non positive minMessagesPerSegment")
    void rejectsNonPositiveMinMessagesPerSegment() {
        assertThatThrownBy(
                        () ->
                                new ConversationChunkingConfig(
                                        10, ConversationSegmentStrategy.LLM, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minMessagesPerSegment");
    }
}
