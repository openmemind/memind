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
package com.openmemind.ai.memory.core.retrieval.admission;

public record RetrievalAdmissionOptions(int maxQueryChars, int maxQueryTokens) {

    public RetrievalAdmissionOptions {
        if (maxQueryChars <= 0) {
            throw new IllegalArgumentException("maxQueryChars must be positive");
        }
        if (maxQueryTokens <= 0) {
            throw new IllegalArgumentException("maxQueryTokens must be positive");
        }
    }

    public static RetrievalAdmissionOptions defaults() {
        return new RetrievalAdmissionOptions(4000, 512);
    }
}
