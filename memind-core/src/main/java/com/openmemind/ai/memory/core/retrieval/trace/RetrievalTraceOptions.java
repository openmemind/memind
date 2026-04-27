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
package com.openmemind.ai.memory.core.retrieval.trace;

public record RetrievalTraceOptions(int maxStages, int maxCandidatesPerStage, int maxTextLength) {

    public static RetrievalTraceOptions defaults() {
        return new RetrievalTraceOptions(32, 8, 160);
    }

    public RetrievalTraceOptions {
        maxStages = Math.max(1, maxStages);
        maxCandidatesPerStage = Math.max(0, maxCandidatesPerStage);
        maxTextLength = Math.max(0, maxTextLength);
    }
}
