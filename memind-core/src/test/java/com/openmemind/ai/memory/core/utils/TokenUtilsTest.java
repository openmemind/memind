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
package com.openmemind.ai.memory.core.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TokenUtils")
class TokenUtilsTest {

    @Test
    @DisplayName("returns zero for null or blank text")
    void countTokensReturnsZeroForNullOrBlankText() {
        assertThat(TokenUtils.countTokens((String) null)).isZero();
        assertThat(TokenUtils.countTokens("   ")).isZero();
    }

    @Test
    @DisplayName("returns zero for null or empty message list")
    void countTokensReturnsZeroForNullOrEmptyMessageList() {
        assertThat(TokenUtils.countTokens((List<Message>) null)).isZero();
        assertThat(TokenUtils.countTokens(List.of())).isZero();
    }

    @Test
    @DisplayName("aggregates message text with newlines")
    void countTokensAggregatesMessagesWithNewlines() {
        var messages = List.of(Message.user("Hello"), Message.assistant("World"));
        var expected = TokenUtils.countTokens("Hello\nWorld");
        assertThat(TokenUtils.countTokens(messages)).isEqualTo(expected);
    }
}
