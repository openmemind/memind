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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;

/**
 * Builder-owned runtime switches and bounds for deep retrieval memory-thread assist.
 */
public record DeepMemoryThreadAssistOptions(
        boolean enabled,
        int maxThreads,
        int maxMembersPerThread,
        int protectDirectTopK,
        Duration timeout) {

    public DeepMemoryThreadAssistOptions {
        SimpleMemoryThreadAssistOptions.validateAssistShape(
                maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
    }

    public static DeepMemoryThreadAssistOptions defaults() {
        return new DeepMemoryThreadAssistOptions(false, 2, 3, 5, Duration.ofMillis(150));
    }

    @JsonCreator
    public static DeepMemoryThreadAssistOptions fromJson(
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("maxThreads") Integer maxThreads,
            @JsonProperty("maxMembersPerThread") Integer maxMembersPerThread,
            @JsonProperty("protectDirectTopK") Integer protectDirectTopK,
            @JsonProperty("timeout") Duration timeout) {
        var defaults = defaults();
        return new DeepMemoryThreadAssistOptions(
                enabled != null ? enabled : defaults.enabled(),
                maxThreads != null ? maxThreads : defaults.maxThreads(),
                maxMembersPerThread != null ? maxMembersPerThread : defaults.maxMembersPerThread(),
                protectDirectTopK != null ? protectDirectTopK : defaults.protectDirectTopK(),
                timeout != null ? timeout : defaults.timeout());
    }

    public DeepMemoryThreadAssistOptions withEnabled(boolean enabled) {
        return new DeepMemoryThreadAssistOptions(
                enabled, maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
    }

    public DeepMemoryThreadAssistOptions withMaxMembersPerThread(int maxMembersPerThread) {
        return new DeepMemoryThreadAssistOptions(
                enabled, maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
    }

    public DeepMemoryThreadAssistOptions withProtectDirectTopK(int protectDirectTopK) {
        return new DeepMemoryThreadAssistOptions(
                enabled, maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
    }

    public DeepMemoryThreadAssistOptions withTimeout(Duration timeout) {
        return new DeepMemoryThreadAssistOptions(
                enabled, maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
    }
}
