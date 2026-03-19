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
package com.openmemind.ai.memory.core.retrieval.deep;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Query expansion interface with type annotations
 *
 * <p>Based on the original query and information gaps, generate expanded queries with
 * {@link ExpandedQuery.QueryType} annotations,
 * so that downstream can route to different retrieval channels (BM25 / vector search) based on the type.
 *
 */
public interface TypedQueryExpander {

    /**
     * Generate expanded queries with type annotations based on the original query + gaps
     *
     * @param query Original query
     * @param gaps SufficiencyResult.gaps, description of missing information (max 3)
     * @param keyInformation Known key information (for pronoun resolution and avoiding redundant searches)
     * @param conversationHistory Conversation history (can be empty)
     * @param maxExpansions Maximum number of expansions
     * @return List of expanded queries with type annotations, excluding the original query
     */
    Mono<List<ExpandedQuery>> expand(
            String query,
            List<String> gaps,
            List<String> keyInformation,
            List<String> conversationHistory,
            int maxExpansions);
}
