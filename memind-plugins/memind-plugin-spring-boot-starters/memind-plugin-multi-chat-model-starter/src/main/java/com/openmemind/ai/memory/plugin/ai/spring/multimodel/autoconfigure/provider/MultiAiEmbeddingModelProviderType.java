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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider;

public enum MultiAiEmbeddingModelProviderType {
    OPENAI("openai", "OpenAI"),
    GOOGLE("google", "Google GenAI");

    private final String propertyValue;
    private final String displayName;

    MultiAiEmbeddingModelProviderType(String propertyValue, String displayName) {
        this.propertyValue = propertyValue;
        this.displayName = displayName;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public String displayName() {
        return displayName;
    }
}
