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
package com.openmemind.ai.memory.autoconfigure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for multi-LLM routing.
 *
 * <p>Maps {@code memind.llm.slots.<slot-name>} to the bean name of a
 * {@link com.openmemind.ai.memory.core.llm.StructuredChatClient}.
 */
@ConfigurationProperties(prefix = "memind.llm")
public class MemoryLlmProperties {

    private Map<String, String> slots = new HashMap<>();

    public Map<String, String> getSlots() {
        return slots;
    }

    public void setSlots(Map<String, String> slots) {
        this.slots = slots;
    }
}
