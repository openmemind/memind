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
package com.openmemind.ai.memory.plugin.rawdata.agent.config;

import java.util.List;

/**
 * Privacy controls for coding-agent timeline capture.
 */
public record AgentPrivacyOptions(
        boolean redactSecrets,
        int maxInputChars,
        int maxOutputChars,
        boolean captureFileContent,
        List<String> denyPathPatterns,
        List<String> allowPathPatterns) {

    public AgentPrivacyOptions() {
        this(true, 2_000, 4_000, false, List.of(".env", "*.pem", "*.key"), List.of());
    }

    public AgentPrivacyOptions {
        maxInputChars = Math.max(0, maxInputChars);
        maxOutputChars = Math.max(0, maxOutputChars);
        denyPathPatterns = denyPathPatterns == null ? List.of() : List.copyOf(denyPathPatterns);
        allowPathPatterns = allowPathPatterns == null ? List.of() : List.copyOf(allowPathPatterns);
    }
}
