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

/**
 * Bounded preview of a ranked candidate shown inside a stage trace.
 *
 * <p>The trace intentionally stores a text preview rather than the full scored result. Full result
 * data remains in the normal retrieval response and source stores.
 */
public record RetrievalCandidateTrace(
        String sourceType, int rank, Double finalScore, Float vectorScore, String textPreview) {}
