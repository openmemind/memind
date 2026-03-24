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
package com.openmemind.ai.memory.plugin.ai.spring.autoconfigure;

import com.openmemind.ai.memory.autoconfigure.MemoryProperties;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.plugin.ai.spring.FileSimpleVectorStore;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiMemoryVector;
import java.nio.file.Path;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(
        name = {
            "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
            "org.springframework.ai.model.vertexai.autoconfigure.embedding.VertexAiTextEmbeddingAutoConfiguration",
            "org.springframework.ai.model.vertexai.autoconfigure.embedding.VertexAiMultiModalEmbeddingAutoConfiguration"
        })
@ConditionalOnClass(EmbeddingModel.class)
@ConditionalOnBean(EmbeddingModel.class)
@EnableConfigurationProperties(MemoryProperties.class)
public class SpringAiVectorAutoConfiguration {

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean({VectorStore.class, MemoryVector.class})
    public VectorStore vectorStore(EmbeddingModel embeddingModel, MemoryProperties properties) {
        return new FileSimpleVectorStore(
                embeddingModel, Path.of(properties.getVector().getStorePath()));
    }

    @Bean
    @ConditionalOnMissingBean(MemoryVector.class)
    public MemoryVector memoryVector(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        return new SpringAiMemoryVector(vectorStore, embeddingModel);
    }
}
