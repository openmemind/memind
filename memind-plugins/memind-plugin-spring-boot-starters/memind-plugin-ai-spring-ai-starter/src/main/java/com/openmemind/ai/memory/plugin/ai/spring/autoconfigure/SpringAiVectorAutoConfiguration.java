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

import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.plugin.ai.spring.FileSimpleVectorStore;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiMemoryVector;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.MultiAiModelAutoConfiguration;
import java.nio.file.Path;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = MultiAiModelAutoConfiguration.class)
@ConditionalOnClass(EmbeddingModel.class)
@EnableConfigurationProperties(SpringAiVectorProperties.class)
public class SpringAiVectorAutoConfiguration {

    @Bean(destroyMethod = "")
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnMissingBean({VectorStore.class, MemoryVector.class})
    public VectorStore vectorStore(
            EmbeddingModel embeddingModel, SpringAiVectorProperties properties) {
        return new FileSimpleVectorStore(embeddingModel, Path.of(properties.getStorePath()));
    }

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnMissingBean(MemoryVector.class)
    public MemoryVector memoryVector(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        return new SpringAiMemoryVector(vectorStore, embeddingModel);
    }
}
