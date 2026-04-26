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
import java.util.Objects;

/**
 * Builder-owned runtime switches and bounds for simple retrieval memory-thread assist.
 */
public record SimpleMemoryThreadAssistOptions(
        boolean enabled,
        int maxThreads,
        int maxMembersPerThread,
        int protectDirectTopK,
        Duration timeout) {

    public SimpleMemoryThreadAssistOptions {
        validateAssistShape(maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
    }

    public static void validateAssistShape(
            int maxThreads, int maxMembersPerThread, int protectDirectTopK, Duration timeout) {
        if (maxThreads <= 0 || maxMembersPerThread <= 0) {
            throw new IllegalArgumentException("memory thread assist bounds must be positive");
        }
        if (protectDirectTopK < 0) {
            throw new IllegalArgumentException("protectDirectTopK must be non-negative");
        }
        Objects.requireNonNull(timeout, "timeout");
    }

    public static SimpleMemoryThreadAssistOptions defaults() {
        return new SimpleMemoryThreadAssistOptions(false, 2, 3, 3, Duration.ofMillis(150));
    }

    @JsonCreator
    public static SimpleMemoryThreadAssistOptions fromJson(
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("maxThreads") Integer maxThreads,
            @JsonProperty("maxMembersPerThread") Integer maxMembersPerThread,
            @JsonProperty("protectDirectTopK") Integer protectDirectTopK,
            @JsonProperty("timeout") Duration timeout) {
        var defaults = defaults();
        return new SimpleMemoryThreadAssistOptions(
                enabled != null ? enabled : defaults.enabled(),
                maxThreads != null ? maxThreads : defaults.maxThreads(),
                maxMembersPerThread != null ? maxMembersPerThread : defaults.maxMembersPerThread(),
                protectDirectTopK != null ? protectDirectTopK : defaults.protectDirectTopK(),
                timeout != null ? timeout : defaults.timeout());
    }

    public SimpleMemoryThreadAssistOptions withEnabled(boolean enabled) {
        return new SimpleMemoryThreadAssistOptions(
                enabled, maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
    }

    public SimpleMemoryThreadAssistOptions withMaxMembersPerThread(int maxMembersPerThread) {
        return new SimpleMemoryThreadAssistOptions(
                enabled, maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
    }

    public SimpleMemoryThreadAssistOptions withProtectDirectTopK(int protectDirectTopK) {
        return new SimpleMemoryThreadAssistOptions(
                enabled, maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
    }

    public SimpleMemoryThreadAssistOptions withTimeout(Duration timeout) {
        return new SimpleMemoryThreadAssistOptions(
                enabled, maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
    }
}
