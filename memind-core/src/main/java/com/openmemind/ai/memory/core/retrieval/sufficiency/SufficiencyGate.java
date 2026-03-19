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
package com.openmemind.ai.memory.core.retrieval.sufficiency;

import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Sufficiency Gate Interface
 *
 * <p>Determine whether the current results are sufficient between each layer of retrieval, and continue to
 * drill down only if they are not sufficient
 *
 */
public interface SufficiencyGate {

    /**
     * Check the sufficiency of the current results
     *
     * @param context Query context
     * @param results Current layer's retrieval results
     * @return SufficiencyResult, containing sufficient/reasoning/evidences/gaps
     */
    Mono<SufficiencyResult> check(QueryContext context, List<ScoredResult> results);
}
