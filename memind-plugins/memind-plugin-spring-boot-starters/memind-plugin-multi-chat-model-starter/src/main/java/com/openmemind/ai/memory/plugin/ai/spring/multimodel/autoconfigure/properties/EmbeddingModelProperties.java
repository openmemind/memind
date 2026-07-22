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

import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiEmbeddingModelProviderType;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public final class EmbeddingModelProperties {

    /**
     * Embedding model provider type.
     */
    private MultiAiEmbeddingModelProviderType type;

    @NestedConfigurationProperty
    private MultiAiOpenAiEmbeddingProperties openai = new MultiAiOpenAiEmbeddingProperties();

    @NestedConfigurationProperty
    private MultiAiGoogleGenAiEmbeddingProperties google =
            new MultiAiGoogleGenAiEmbeddingProperties();

    public MultiAiEmbeddingModelProviderType getType() {
        return type;
    }

    public void setType(MultiAiEmbeddingModelProviderType type) {
        this.type = type;
    }

    public MultiAiOpenAiEmbeddingProperties getOpenai() {
        return openai;
    }

    public void setOpenai(MultiAiOpenAiEmbeddingProperties openai) {
        this.openai = openai;
    }

    public MultiAiGoogleGenAiEmbeddingProperties getGoogle() {
        return google;
    }

    public void setGoogle(MultiAiGoogleGenAiEmbeddingProperties google) {
        this.google = google;
    }
}
