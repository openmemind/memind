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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.memory.autoconfigure.MemoryExtractionProperties;
import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig;
import com.openmemind.ai.memory.core.extraction.context.ContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.context.LlmContextCommitDetector;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.evaluation.checkpoint.CheckpointStore;
import com.openmemind.ai.memory.plugin.store.mybatis.MybatisPlusInsightBuffer;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.InsightBufferMapper;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * Evaluation-specific Spring configuration.
 *
 * <p>The bulk of the extraction and retrieval pipeline is provided by memind-spring-boot-starter.
 * This class only overrides or adds beans specific to the evaluation environment.
 */
@Configuration
public class EvaluationConfiguration {

    // ─── Infrastructure ─────────────────────────────

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public CheckpointStore checkpointStore(ObjectMapper mapper, EvaluationProperties props) {
        return new CheckpointStore(mapper, java.nio.file.Path.of(props.getOutputDir()));
    }

    // ─── HTTP Client ────────────────────────────────

    /**
     * Shared Reactor Netty HttpClient to avoid PrematureCloseException:
     * creates a new connection per request instead of pooling.
     */
    @Bean
    public HttpClient reactorHttpClient() {
        return HttpClient.create(ConnectionProvider.newConnection())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(300));
    }

    /**
     * RestClient.Builder using JDK HttpClient to avoid Netty dirty connection issues
     * in synchronous Spring AI ChatModel calls.
     */
    @Bean
    public org.springframework.web.client.RestClient.Builder restClientBuilder() {
        var jdkHttpClient =
                java.net.http.HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
        var requestFactory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(300));
        return org.springframework.web.client.RestClient.builder().requestFactory(requestFactory);
    }

    /** WebClient.Builder for async calls (e.g. embedding), reusing the Netty connection. */
    @Bean
    public WebClient.Builder webClientBuilder(HttpClient httpClient) {
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    // ─── Overrides ──────────────────────────────────

    /**
     * Override starter's default ContextCommitDetector (rule-based) with LLM-based detection
     * for more accurate conversation segmentation during evaluation.
     */
    @Bean
    public ContextCommitDetector boundaryDetector(
            StructuredChatClient structuredChatClient, MemoryExtractionProperties props) {
        var b = props.getBoundary();
        var config =
                new CommitDetectorConfig(
                        b.getMaxMessages(), b.getMaxTokens(), b.getMinMessagesForLlm());
        return new LlmContextCommitDetector(config, structuredChatClient);
    }

    /**
     * Override starter's InMemoryInsightBuffer with MyBatis-backed persistent store
     * so that the insight buffer survives restarts during long benchmark runs.
     */
    @Bean
    public InsightBuffer insightBufferStore(InsightBufferMapper insightBufferMapper) {
        return new MybatisPlusInsightBuffer(insightBufferMapper);
    }
}
