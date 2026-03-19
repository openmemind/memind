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
package com.openmemind.ai.memory.core.retrieval.tier;

import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Insight type router
 *
 * <p>Selects the types related to the current query from the available insight types
 * based on user queries and conversation history.
 * Used to replace brute-force vector similarity matching, implementing LLM-based BRANCH level routing.
 *
 */
public interface InsightTypeRouter {

    /**
     * Routes the query to the relevant insight types
     *
     * @param query               User query
     * @param conversationHistory Conversation history
     * @param availableTypes      Mapping of available types' name → description
     * @return List of activated type names selected by LLM
     */
    Mono<List<String>> route(
            String query, List<String> conversationHistory, Map<String, String> availableTypes);
}
