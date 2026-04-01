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
package com.openmemind.ai.memory.server.service.config;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.server.domain.config.response.MemoryOptionsSnapshot;
import com.openmemind.ai.memory.server.domain.config.view.MemoryOptionItemView;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeFactory;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeUnavailableException;
import java.util.List;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;

public class MemoryOptionService {

    public static final String CONFIG_KEY = "memory_options";

    private final ServerRuntimeConfigRepository repository;
    private final MemoryOptionsProjectionMapper projectionMapper;
    private final MemoryOptionsCodec codec;
    private final MemoryRuntimeManager runtimeManager;
    private final MemoryRuntimeFactory runtimeFactory;

    public MemoryOptionService(
            ServerRuntimeConfigRepository repository,
            MemoryOptionsProjectionMapper projectionMapper,
            MemoryOptionsCodec codec,
            MemoryRuntimeManager runtimeManager,
            MemoryRuntimeFactory runtimeFactory) {
        this.repository = repository;
        this.projectionMapper = projectionMapper;
        this.codec = codec;
        this.runtimeManager = runtimeManager;
        this.runtimeFactory = runtimeFactory;
    }

    public MemoryOptionsSnapshot getCurrent() {
        MemoryOptionsState state = loadOrInitialize();
        return snapshot(state.version(), state.options());
    }

    public MemoryOptionsSnapshot update(
            long expectedVersion, Map<String, List<MemoryOptionItemView>> config) {
        MemoryOptionsState current = loadOrInitialize();
        if (current.version() != expectedVersion) {
            throw versionConflict(expectedVersion, current.version());
        }
        MemoryBuildOptions nextOptions = projectionMapper.toOptions(config);
        Memory nextMemory = requireRuntimeFactory().create(nextOptions);
        try {
            if (!repository.update(CONFIG_KEY, expectedVersion, codec.write(nextOptions))) {
                closeQuietly(nextMemory);
                throw versionConflict(expectedVersion, runtimeManager.currentVersion());
            }
            runtimeManager.swap(nextMemory, nextOptions, expectedVersion + 1);
            return snapshot(expectedVersion + 1, nextOptions);
        } catch (RuntimeException e) {
            if (!(e instanceof OptimisticLockingFailureException)) {
                // Build/runtime exceptions should leave the current runtime untouched.
            }
            throw e;
        }
    }

    private MemoryOptionsSnapshot snapshot(long version, MemoryBuildOptions options) {
        return new MemoryOptionsSnapshot(version, projectionMapper.toProjection(options));
    }

    private MemoryOptionsState loadOrInitialize() {
        return repository
                .findActive(CONFIG_KEY)
                .map(
                        config ->
                                new MemoryOptionsState(
                                        config.getConfigVersion(),
                                        codec.read(config.getConfigJson())))
                .orElseGet(this::initializeDefaults);
    }

    private MemoryOptionsState initializeDefaults() {
        MemoryBuildOptions defaults = MemoryBuildOptions.defaults();
        repository.insertInitial(CONFIG_KEY, 1L, codec.write(defaults));
        return new MemoryOptionsState(1L, defaults);
    }

    private static OptimisticLockingFailureException versionConflict(
            long expectedVersion, long actualVersion) {
        return new OptimisticLockingFailureException(
                "Memory options version conflict, expected "
                        + expectedVersion
                        + " but was "
                        + actualVersion);
    }

    private MemoryRuntimeFactory requireRuntimeFactory() {
        if (runtimeFactory == null) {
            throw new MemoryRuntimeUnavailableException(
                    "Memory runtime dependencies are unavailable. Configure the required runtime"
                            + " beans before updating memory options.");
        }
        return runtimeFactory;
    }

    private static void closeQuietly(Memory memory) {
        try {
            memory.close();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to close unused memory runtime", e);
        }
    }

    private record MemoryOptionsState(long version, MemoryBuildOptions options) {}
}
