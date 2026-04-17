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
package com.openmemind.ai.memory.core.builder;

import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.retrieval.DefaultMemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.cache.CaffeineRetrievalCache;
import com.openmemind.ai.memory.core.retrieval.cache.RetrievalCache;
import com.openmemind.ai.memory.core.retrieval.deep.LlmTypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.graph.DefaultRetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.sufficiency.LlmSufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTypeRouter;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.LlmInsightTypeRouter;
import com.openmemind.ai.memory.core.tracing.decorator.TracingRetrievalGraphAssistant;

final class MemoryRetrievalAssembler {

    DefaultMemoryRetriever assemble(MemoryAssemblyContext context) {
        ChatClientRegistry registry = context.chatClientRegistry();
        InsightTypeRouter insightTypeRouter =
                new LlmInsightTypeRouter(
                        registry.resolve(ChatClientSlot.INSIGHT_TYPE_ROUTER),
                        context.promptRegistry());
        InsightTierRetriever insightTierRetriever =
                new InsightTierRetriever(
                        context.memoryStore(), context.memoryVector(), insightTypeRouter);
        ItemTierRetriever itemTierRetriever =
                new ItemTierRetriever(
                        context.memoryStore(), context.memoryVector(), context.textSearch());
        SufficiencyGate sufficiencyGate =
                new LlmSufficiencyGate(
                        registry.resolve(ChatClientSlot.SUFFICIENCY_GATE),
                        context.promptRegistry());
        TypedQueryExpander typedQueryExpander =
                new LlmTypedQueryExpander(
                        registry.resolve(ChatClientSlot.QUERY_EXPANDER), context.promptRegistry());
        DeepRetrievalStrategy deepRetrievalStrategy =
                new DeepRetrievalStrategy(
                        insightTierRetriever,
                        itemTierRetriever,
                        sufficiencyGate,
                        typedQueryExpander,
                        context.reranker(),
                        context.memoryStore());
        RetrievalGraphAssistant graphAssistant =
                new TracingRetrievalGraphAssistant(
                        new DefaultRetrievalGraphAssistant(context.memoryStore()),
                        context.memoryObserver());
        SimpleRetrievalStrategy simpleRetrievalStrategy =
                new SimpleRetrievalStrategy(
                        insightTierRetriever,
                        itemTierRetriever,
                        context.textSearch(),
                        context.memoryStore(),
                        graphAssistant);

        RetrievalCache retrievalCache = new CaffeineRetrievalCache();
        DefaultMemoryRetriever memoryRetriever =
                new DefaultMemoryRetriever(
                        retrievalCache, context.memoryStore(), context.textSearch());
        memoryRetriever.registerStrategy(simpleRetrievalStrategy);
        memoryRetriever.registerStrategy(deepRetrievalStrategy);
        return memoryRetriever;
    }
}
