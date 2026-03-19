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
