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
package com.openmemind.ai.memory.server.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memind.observability")
public class MemindServerObservabilityProperties {

    private final RetrievalTrace retrievalTrace = new RetrievalTrace();

    public RetrievalTrace getRetrievalTrace() {
        return retrievalTrace;
    }

    public static class RetrievalTrace {

        private boolean enabled;
        private int maxStages = 32;
        private int maxCandidatesPerStage = 8;
        private int maxTextLength = 160;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxStages() {
            return maxStages;
        }

        public void setMaxStages(int maxStages) {
            this.maxStages = maxStages;
        }

        public int getMaxCandidatesPerStage() {
            return maxCandidatesPerStage;
        }

        public void setMaxCandidatesPerStage(int maxCandidatesPerStage) {
            this.maxCandidatesPerStage = maxCandidatesPerStage;
        }

        public int getMaxTextLength() {
            return maxTextLength;
        }

        public void setMaxTextLength(int maxTextLength) {
            this.maxTextLength = maxTextLength;
        }
    }
}
