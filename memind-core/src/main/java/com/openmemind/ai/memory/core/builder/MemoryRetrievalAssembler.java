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

import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.retrieval.DefaultMemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.cache.CaffeineRetrievalCache;
import com.openmemind.ai.memory.core.retrieval.cache.RetrievalCache;
import com.openmemind.ai.memory.core.retrieval.deep.LlmTypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.sufficiency.LlmSufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTypeRouter;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.LlmInsightTypeRouter;

final class MemoryRetrievalAssembler {

    DefaultMemoryRetriever assemble(MemoryAssemblyContext context) {
        StructuredChatClient chatClient = context.chatClient();
        InsightTypeRouter insightTypeRouter = new LlmInsightTypeRouter(chatClient);
        InsightTierRetriever insightTierRetriever =
                new InsightTierRetriever(
                        context.memoryStore(), context.memoryVector(), insightTypeRouter);
        ItemTierRetriever itemTierRetriever =
                new ItemTierRetriever(
                        context.memoryStore(), context.memoryVector(), context.textSearch());
        SufficiencyGate sufficiencyGate = new LlmSufficiencyGate(chatClient);
        TypedQueryExpander typedQueryExpander = new LlmTypedQueryExpander(chatClient);
        DeepRetrievalStrategy deepRetrievalStrategy =
                new DeepRetrievalStrategy(
                        insightTierRetriever,
                        itemTierRetriever,
                        sufficiencyGate,
                        typedQueryExpander,
                        context.reranker(),
                        context.memoryStore());
        SimpleRetrievalStrategy simpleRetrievalStrategy =
                new SimpleRetrievalStrategy(
                        insightTierRetriever,
                        itemTierRetriever,
                        context.textSearch(),
                        context.memoryStore());

        RetrievalCache retrievalCache = new CaffeineRetrievalCache();
        DefaultMemoryRetriever memoryRetriever =
                new DefaultMemoryRetriever(
                        retrievalCache, context.memoryStore(), context.textSearch());
        memoryRetriever.registerStrategy(simpleRetrievalStrategy);
        memoryRetriever.registerStrategy(deepRetrievalStrategy);
        return memoryRetriever;
    }
}
