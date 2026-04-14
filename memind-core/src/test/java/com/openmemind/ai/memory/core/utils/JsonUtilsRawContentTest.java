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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonUtilsRawContentTest {

    @Test
    void jsonUtilsCanRoundTripCoreRawContent() {
        RawContent content = new ConversationContent(List.of());

        String json = JsonUtils.toJson(content);
        RawContent decoded = JsonUtils.fromJson(json, RawContent.class);

        assertThat(decoded).isInstanceOf(ConversationContent.class);
    }

    @Test
    void jsonUtilsRejectsUnknownPluginOwnedRawContentWithoutExplicitSubtypeRegistration() {
        assertThatThrownBy(
                        () ->
                                JsonUtils.fromJson(
                                        """
                                        {"type":"tool_call","calls":[]}
                                        """,
                                        RawContent.class))
                .isInstanceOf(JsonUtils.JsonException.class)
                .hasMessageContaining("RawContent");
    }
}
