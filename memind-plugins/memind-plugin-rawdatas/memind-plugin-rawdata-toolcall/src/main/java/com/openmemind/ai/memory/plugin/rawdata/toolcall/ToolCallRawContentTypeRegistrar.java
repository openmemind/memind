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
package com.openmemind.ai.memory.plugin.rawdata.toolcall;

import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.content.ToolCallContent;
import java.util.Map;

/**
 * Plugin-owned ToolCall raw content type registrar.
 */
public final class ToolCallRawContentTypeRegistrar implements RawContentTypeRegistrar {

    @Override
    public Map<String, Class<? extends RawContent>> subtypes() {
        return Map.of("tool_call", ToolCallContent.class);
    }
}
