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
