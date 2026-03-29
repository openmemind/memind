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
package com.openmemind.ai.memory.autoconfigure.retrieval;

import com.openmemind.ai.memory.autoconfigure.MemoryRetrievalProperties;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.rerank.LlmReranker;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.retrieval.DefaultMemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig.RerankConfig;
import com.openmemind.ai.memory.core.retrieval.cache.CaffeineRetrievalCache;
import com.openmemind.ai.memory.core.retrieval.cache.RetrievalCache;
import com.openmemind.ai.memory.core.retrieval.deep.LlmTypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig.FusionConfig;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig.KeywordSearchConfig;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig.PositionBonusConfig;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig.QueryWeightConfig;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig.RecencyConfig;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig.TimeDecayConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepStrategyConfig.QueryExpansionConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepStrategyConfig.SufficiencyConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.sufficiency.LlmSufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTypeRouter;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.LlmInsightTypeRouter;
import com.openmemind.ai.memory.core.retrieval.tier.RawDataTierRetriever;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

/**
 * Retrieval pipeline auto-configuration.
 *
 * <p>Mirrors all beans from the server's {@code RetrievalConfiguration}, each guarded by
 * {@link ConditionalOnMissingBean} so that users can override any bean.
 *
 */
@AutoConfiguration
@AutoConfigureAfter(
        name = {
            "com.openmemind.ai.memory.plugin.store.mybatis.MemoryMybatisPlusAutoConfiguration",
            "com.openmemind.ai.memory.plugin.jdbc.autoconfigure.JdbcPluginAutoConfiguration",
            "com.openmemind.ai.memory.plugin.ai.spring.autoconfigure.SpringAiLlmAutoConfiguration",
            "com.openmemind.ai.memory.plugin.ai.spring.autoconfigure.SpringAiVectorAutoConfiguration"
        })
@EnableConfigurationProperties(MemoryRetrievalProperties.class)
public class MemoryRetrievalAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ScoringConfig scoringConfig(MemoryRetrievalProperties props) {
        var s = props.getScoring();
        return new ScoringConfig(
                new FusionConfig(
                        s.getFusion().getK(),
                        s.getFusion().getVectorWeight(),
                        s.getFusion().getKeywordWeight()),
                new TimeDecayConfig(
                        s.getTimeDecay().getRate(),
                        s.getTimeDecay().getFloor(),
                        s.getTimeDecay().getOutOfRangePenalty()),
                new RecencyConfig(s.getRecency().getRate(), s.getRecency().getFloor()),
                new PositionBonusConfig(
                        s.getPositionBonus().getTop1(), s.getPositionBonus().getTop3()),
                new KeywordSearchConfig(
                        s.getKeywordSearch().getProbeTopK(),
                        s.getKeywordSearch().getStrongSignalMinScore(),
                        s.getKeywordSearch().getStrongSignalMinGap()),
                new QueryWeightConfig(
                        s.getQueryWeight().getOriginalWeight(),
                        s.getQueryWeight().getExpandedWeight()),
                s.getCandidateMultiplier(),
                s.getRerankCandidateLimit(),
                s.getRawDataKeyInfoMaxLines(),
                s.getInsightLlmThreshold());
    }

    @Bean
    @ConditionalOnMissingBean
    public RetrievalConfig retrievalConfig(
            MemoryRetrievalProperties props, ScoringConfig scoringConfig) {
        var d = props.getDeep();
        var deepConfig =
                new DeepStrategyConfig(
                        new QueryExpansionConfig(
                                d.getQueryExpansion().getMaxExpandedQueries(),
                                d.getQueryExpansion().getOriginalWeight(),
                                d.getQueryExpansion().getExpandedWeight()),
                        new SufficiencyConfig(d.getSufficiency().getItemTopK()),
                        d.getTier2InitTopK(),
                        d.getBm25InitTopK(),
                        d.getMinScore());
        var r = props.getRerank();
        var rerankConfig =
                r.isEnabled()
                        ? (r.isBlendWithRetrieval()
                                ? RerankConfig.blend(r.getTopK())
                                : RerankConfig.pure(r.getTopK()))
                        : RerankConfig.disabled();
        var base =
                RetrievalConfig.deep(deepConfig)
                        .withRerank(rerankConfig)
                        .withScoring(scoringConfig)
                        .withTimeout(props.getTimeout());
        return props.isEnableCache() ? base : base.withoutCache();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "memind.retrieval.rerank",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public Reranker llmReranker(MemoryRetrievalProperties props) {
        var r = props.getRerank();
        return new LlmReranker(r.getBaseUrl(), r.getApiKey(), r.getModel());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "memind.retrieval.rerank",
            name = "enabled",
            havingValue = "false")
    public Reranker noOpReranker() {
        return (query, results, topK) -> {
            var sorted =
                    results.stream()
                            .sorted(Comparator.comparingDouble(ScoredResult::finalScore).reversed())
                            .limit(topK)
                            .toList();
            return Mono.just(sorted);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public RetrievalCache retrievalCache() {
        return new CaffeineRetrievalCache();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChatClientRegistry.class)
    public InsightTypeRouter insightTypeRouter(
            ChatClientRegistry chatClientRegistry, PromptRegistry promptRegistry) {
        return new LlmInsightTypeRouter(
                chatClientRegistry.resolve(ChatClientSlot.INSIGHT_TYPE_ROUTER), promptRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({MemoryVector.class, InsightTypeRouter.class})
    public InsightTierRetriever insightTierRetriever(
            MemoryStore store, MemoryVector vector, InsightTypeRouter router) {
        return new InsightTierRetriever(store, vector, router);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MemoryVector.class)
    public ItemTierRetriever itemTierRetriever(
            MemoryStore store,
            MemoryVector vector,
            @Autowired(required = false) MemoryTextSearch textSearch) {
        return new ItemTierRetriever(store, vector, textSearch);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MemoryVector.class)
    public RawDataTierRetriever rawDataTierRetriever(
            MemoryStore store,
            MemoryVector vector,
            @Autowired(required = false) MemoryTextSearch textSearch) {
        return new RawDataTierRetriever(store, vector, textSearch);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChatClientRegistry.class)
    public SufficiencyGate sufficiencyGate(
            ChatClientRegistry chatClientRegistry, PromptRegistry promptRegistry) {
        return new LlmSufficiencyGate(
                chatClientRegistry.resolve(ChatClientSlot.SUFFICIENCY_GATE), promptRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChatClientRegistry.class)
    public TypedQueryExpander typedQueryExpander(
            ChatClientRegistry chatClientRegistry, PromptRegistry promptRegistry) {
        return new LlmTypedQueryExpander(
                chatClientRegistry.resolve(ChatClientSlot.QUERY_EXPANDER), promptRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
        InsightTierRetriever.class,
        ItemTierRetriever.class,
        SufficiencyGate.class,
        TypedQueryExpander.class
    })
    public DeepRetrievalStrategy deepRetrievalStrategy(
            InsightTierRetriever insightTierRetriever,
            ItemTierRetriever itemTierRetriever,
            SufficiencyGate sufficiencyGate,
            TypedQueryExpander typedQueryExpander,
            Reranker reranker,
            MemoryStore memoryStore) {
        return new DeepRetrievalStrategy(
                insightTierRetriever,
                itemTierRetriever,
                sufficiencyGate,
                typedQueryExpander,
                reranker,
                memoryStore);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({InsightTierRetriever.class, ItemTierRetriever.class})
    public SimpleRetrievalStrategy simpleRetrievalStrategy(
            InsightTierRetriever insightTierRetriever,
            ItemTierRetriever itemTierRetriever,
            @Autowired(required = false) MemoryTextSearch textSearch,
            MemoryStore memoryStore) {
        return new SimpleRetrievalStrategy(
                insightTierRetriever, itemTierRetriever, textSearch, memoryStore);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryRetriever.class)
    @ConditionalOnBean(RetrievalStrategy.class)
    public DefaultMemoryRetriever defaultMemoryRetriever(
            RetrievalCache retrievalCache,
            MemoryStore store,
            @Autowired(required = false) MemoryTextSearch textSearch,
            List<RetrievalStrategy> retrievalStrategies) {
        var retriever = new DefaultMemoryRetriever(retrievalCache, store, textSearch);
        retrievalStrategies.forEach(retriever::registerStrategy);
        return retriever;
    }
}
