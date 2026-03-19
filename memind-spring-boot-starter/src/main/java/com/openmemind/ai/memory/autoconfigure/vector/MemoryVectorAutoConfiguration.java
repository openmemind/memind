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
package com.openmemind.ai.memory.autoconfigure.vector;

import com.openmemind.ai.memory.autoconfigure.MemoryProperties;
import com.openmemind.ai.memory.core.vector.FileSimpleVectorStore;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.SpringAiMemoryVector;
import java.nio.file.Path;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Vector store auto-configuration
 *
 * <p>By default, uses FileSimpleVectorStore, automatically replaced when users introduce other
 * Spring AI VectorStore starters.
 *
 * @author starboyate
 */
@AutoConfiguration
@ConditionalOnClass(EmbeddingModel.class)
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryVectorAutoConfiguration {

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(VectorStore.class)
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
