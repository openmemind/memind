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
package com.openmemind.ai.memory.core.retrieval.thread;

import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;

/**
 * Shared clamp-aware mapper from builder-owned retrieval options to runtime strategy configs.
 */
public final class MemoryThreadAssistConfigMapper {

    private MemoryThreadAssistConfigMapper() {}

    public static SimpleStrategyConfig.MemoryThreadAssistConfig toSimpleConfig(
            MemoryBuildOptions options) {
        var requested = options.retrieval().simple().memoryThreadAssist();
        return new SimpleStrategyConfig.MemoryThreadAssistConfig(
                options.memoryThread().enabled() && requested.enabled(),
                requested.maxThreads(),
                clamp(
                        options.memoryThread().rule().maxRetrievalMembersPerThread(),
                        requested.maxMembersPerThread()),
                requested.protectDirectTopK(),
                requested.timeout());
    }

    public static DeepStrategyConfig.MemoryThreadAssistConfig toDeepConfig(
            MemoryBuildOptions options) {
        var requested = options.retrieval().deep().memoryThreadAssist();
        return new DeepStrategyConfig.MemoryThreadAssistConfig(
                options.memoryThread().enabled() && requested.enabled(),
                requested.maxThreads(),
                clamp(
                        options.memoryThread().rule().maxRetrievalMembersPerThread(),
                        requested.maxMembersPerThread()),
                requested.protectDirectTopK(),
                requested.timeout());
    }

    private static int clamp(int globalMax, int requested) {
        return Math.min(globalMax, requested);
    }
}
