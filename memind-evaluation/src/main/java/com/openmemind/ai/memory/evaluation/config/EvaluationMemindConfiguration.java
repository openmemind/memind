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
package com.openmemind.ai.memory.evaluation.config;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.builder.DeepRetrievalOptions;
import com.openmemind.ai.memory.core.builder.ExtractionCommonOptions;
import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.InsightExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.QueryExpansionOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.builder.RerankMode;
import com.openmemind.ai.memory.core.builder.RerankOptions;
import com.openmemind.ai.memory.core.builder.RetrievalAdvancedOptions;
import com.openmemind.ai.memory.core.builder.RetrievalCommonOptions;
import com.openmemind.ai.memory.core.builder.RetrievalOptions;
import com.openmemind.ai.memory.core.builder.SimpleRetrievalOptions;
import com.openmemind.ai.memory.core.builder.SufficiencyOptions;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.llm.rerank.LlmReranker;
import com.openmemind.ai.memory.core.llm.rerank.NoopReranker;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EvaluationProperties.class)
public class EvaluationMemindConfiguration {

    @Bean
    DataSource dataSource(EvaluationProperties props) throws IOException {
        Path sqlitePath =
                Path.of(props.getSystem().getMemind().getStorage().getSqlite().getPath())
                        .toAbsolutePath()
                        .normalize();
        Path parent = sqlitePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + sqlitePath);
        return dataSource;
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    @Bean
    Reranker reranker(EvaluationProperties props) {
        var rerank = props.getSystem().getMemind().getRetrieval().getRerank();
        if (!rerank.isEnabled()) {
            return new NoopReranker();
        }
        return new LlmReranker(rerank.getBaseUrl(), rerank.getApiKey(), rerank.getModel());
    }

    @Bean
    RetrievalConfig retrievalConfig(EvaluationProperties props) {
        var retrieval = props.getSystem().getMemind().getRetrieval();
        var rerank = retrieval.getRerank();
        int topK = props.getSystem().getSearch().getTopK();
        var rerankConfig =
                rerank.isEnabled()
                        ? (rerank.isBlendWithRetrieval()
                                ? RetrievalConfig.RerankConfig.blend(topK)
                                : RetrievalConfig.RerankConfig.pure(topK))
                        : RetrievalConfig.RerankConfig.disabled();
        return RetrievalConfig.deep().withRerank(rerankConfig).withTimeout(retrieval.getTimeout());
    }

    @Bean
    MemoryBuildOptions memoryBuildOptions(EvaluationProperties props) {
        var memind = props.getSystem().getMemind();
        var boundary = memind.getExtraction().getBoundary();
        var retrieval = memind.getRetrieval();
        var rerank = retrieval.getRerank();
        int topK = props.getSystem().getSearch().getTopK();
        var defaultInsight = InsightExtractionOptions.defaults();
        var defaultRawData = RawDataExtractionOptions.defaults();
        var insight =
                new InsightExtractionOptions(memind.isEnableInsight(), defaultInsight.build());

        return MemoryBuildOptions.builder()
                .extraction(
                        new ExtractionOptions(
                                ExtractionCommonOptions.defaults(),
                                new RawDataExtractionOptions(
                                        defaultRawData.conversation(),
                                        defaultRawData.document(),
                                        defaultRawData.image(),
                                        defaultRawData.audio(),
                                        defaultRawData.toolCall(),
                                        new CommitDetectorConfig(
                                                boundary.getMaxMessages(),
                                                boundary.getMaxTokens(),
                                                boundary.getMinMessagesForLlm()),
                                        defaultRawData.vectorBatchSize()),
                                ItemExtractionOptions.defaults(),
                                insight))
                .retrieval(
                        new RetrievalOptions(
                                RetrievalCommonOptions.defaults(),
                                SimpleRetrievalOptions.defaults(),
                                new DeepRetrievalOptions(
                                        retrieval.getTimeout(),
                                        DeepRetrievalOptions.defaults().insightTopK(),
                                        DeepRetrievalOptions.defaults().itemTopK(),
                                        DeepRetrievalOptions.defaults().rawDataEnabled(),
                                        DeepRetrievalOptions.defaults().rawDataTopK(),
                                        QueryExpansionOptions.defaults(),
                                        SufficiencyOptions.defaults()),
                                new RetrievalAdvancedOptions(
                                        new RerankOptions(
                                                rerank.isEnabled()
                                                        ? (rerank.isBlendWithRetrieval()
                                                                ? RerankMode.BLEND
                                                                : RerankMode.PURE)
                                                        : RerankMode.DISABLED,
                                                topK,
                                                RerankOptions.defaults().top3Weight(),
                                                RerankOptions.defaults().top10Weight(),
                                                RerankOptions.defaults().otherWeight()),
                                        RetrievalAdvancedOptions.defaults().scoring())))
                .build();
    }

    @Bean
    Memory memind(
            StructuredChatClient structuredChatClient,
            MemoryStore memoryStore,
            MemoryBuffer memoryBuffer,
            MemoryVector memoryVector,
            MemoryTextSearch memoryTextSearch,
            Reranker reranker,
            MemoryBuildOptions memoryBuildOptions) {
        return Memory.builder()
                .chatClient(structuredChatClient)
                .store(memoryStore)
                .buffer(memoryBuffer)
                .vector(memoryVector)
                .textSearch(memoryTextSearch)
                .reranker(reranker)
                .options(memoryBuildOptions)
                .build();
    }
}
