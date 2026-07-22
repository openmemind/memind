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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties;

import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelProviderType;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public final class ChatModelProperties {

    /**
     * Chat model provider type.
     */
    private MultiAiChatModelProviderType type;

    @NestedConfigurationProperty
    private MultiAiOpenAiChatProperties openai = new MultiAiOpenAiChatProperties();

    @NestedConfigurationProperty
    private MultiAiAnthropicChatProperties anthropic = new MultiAiAnthropicChatProperties();

    @NestedConfigurationProperty
    private MultiAiGoogleGenAiChatProperties google = new MultiAiGoogleGenAiChatProperties();

    public MultiAiChatModelProviderType getType() {
        return type;
    }

    public void setType(MultiAiChatModelProviderType type) {
        this.type = type;
    }

    public MultiAiOpenAiChatProperties getOpenai() {
        return openai;
    }

    public void setOpenai(MultiAiOpenAiChatProperties openai) {
        this.openai = openai;
    }

    public MultiAiAnthropicChatProperties getAnthropic() {
        return anthropic;
    }

    public void setAnthropic(MultiAiAnthropicChatProperties anthropic) {
        this.anthropic = anthropic;
    }

    public MultiAiGoogleGenAiChatProperties getGoogle() {
        return google;
    }

    public void setGoogle(MultiAiGoogleGenAiChatProperties google) {
        this.google = google;
    }
}
