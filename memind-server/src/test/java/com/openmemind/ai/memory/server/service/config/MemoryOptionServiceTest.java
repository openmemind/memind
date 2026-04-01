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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.server.domain.config.model.ServerRuntimeConfigDO;
import com.openmemind.ai.memory.server.domain.config.response.MemoryOptionsSnapshot;
import com.openmemind.ai.memory.server.domain.config.view.MemoryOptionItemView;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import com.openmemind.ai.memory.server.runtime.RuntimeHandle;
import com.openmemind.ai.memory.server.support.TestMemory;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MemoryOptionServiceTest {

    private final MemoryOptionsProjectionMapper projectionMapper =
            new MemoryOptionsProjectionMapper();
    private final MemoryOptionsCodec codec = new MemoryOptionsCodec(new ObjectMapper());

    @Test
    void getCurrentInitializesDefaultConfigWhenRowMissing() {
        InMemoryServerRuntimeConfigRepository repository =
                new InMemoryServerRuntimeConfigRepository();
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(new TestMemory(), MemoryBuildOptions.defaults(), 1));
        MemoryOptionService service =
                new MemoryOptionService(
                        repository,
                        projectionMapper,
                        codec,
                        runtimeManager,
                        options -> new TestMemory());

        MemoryOptionsSnapshot snapshot = service.getCurrent();

        assertThat(snapshot.version()).isEqualTo(1);
        assertThat(snapshot.config()).containsKeys("extraction", "retrieval");
        assertThat(repository.findActive(MemoryOptionService.CONFIG_KEY))
                .hasValueSatisfying(config -> assertThat(config.getConfigVersion()).isEqualTo(1));
    }

    @Test
    void failedRuntimeBuildKeepsCurrentRuntimeAndDoesNotPersist() {
        InMemoryServerRuntimeConfigRepository repository =
                new InMemoryServerRuntimeConfigRepository();
        repository.insertInitial(
                MemoryOptionService.CONFIG_KEY, 1, codec.write(MemoryBuildOptions.defaults()));
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(new TestMemory(), MemoryBuildOptions.defaults(), 1));
        MemoryOptionService service =
                new MemoryOptionService(
                        repository,
                        projectionMapper,
                        codec,
                        runtimeManager,
                        options -> {
                            throw new IllegalStateException("boom");
                        });

        assertThatThrownBy(() -> service.update(1, projectionConfig(projectionMapper)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");
        assertThat(runtimeManager.currentVersion()).isEqualTo(1);
        assertThat(repository.findActive(MemoryOptionService.CONFIG_KEY))
                .hasValueSatisfying(config -> assertThat(config.getConfigVersion()).isEqualTo(1));
    }

    @Test
    void successfulUpdatePersistsAndSwapsRuntime() {
        InMemoryServerRuntimeConfigRepository repository =
                new InMemoryServerRuntimeConfigRepository();
        repository.insertInitial(
                MemoryOptionService.CONFIG_KEY, 1, codec.write(MemoryBuildOptions.defaults()));
        Memory initialMemory = new TestMemory();
        Memory nextMemory = new TestMemory();
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(initialMemory, MemoryBuildOptions.defaults(), 1));
        MemoryOptionService service =
                new MemoryOptionService(
                        repository, projectionMapper, codec, runtimeManager, options -> nextMemory);

        MemoryOptionsSnapshot snapshot = service.update(1, projectionConfig(projectionMapper));

        assertThat(snapshot.version()).isEqualTo(2);
        assertThat(runtimeManager.currentVersion()).isEqualTo(2);
        assertThat(runtimeManager.currentHandle().memory()).isSameAs(nextMemory);
        assertThat(runtimeManager.currentHandle().options().retrieval().simple().itemTopK())
                .isEqualTo(21);
        assertThat(repository.findActive(MemoryOptionService.CONFIG_KEY))
                .hasValueSatisfying(config -> assertThat(config.getConfigVersion()).isEqualTo(2));
    }

    @Test
    void failedOptimisticUpdateClosesUnusedRuntime() {
        AtomicInteger closeCount = new AtomicInteger();
        FailingUpdateRepository repository = new FailingUpdateRepository();
        repository.insertInitial(
                MemoryOptionService.CONFIG_KEY, 1, codec.write(MemoryBuildOptions.defaults()));
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(new TestMemory(), MemoryBuildOptions.defaults(), 1));
        MemoryOptionService service =
                new MemoryOptionService(
                        repository,
                        projectionMapper,
                        codec,
                        runtimeManager,
                        options -> closeTrackingMemory(closeCount));

        assertThatThrownBy(() -> service.update(1, projectionConfig(projectionMapper)))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(closeCount).hasValue(1);
        assertThat(runtimeManager.currentVersion()).isEqualTo(1);
    }

    private static Map<String, java.util.List<MemoryOptionItemView>> projectionConfig(
            MemoryOptionsProjectionMapper projectionMapper) {
        var projection = projectionMapper.toProjection(MemoryBuildOptions.defaults());
        projection
                .values()
                .forEach(
                        items -> {
                            for (int i = 0; i < items.size(); i++) {
                                MemoryOptionItemView item = items.get(i);
                                if (item.key().equals("retrieval.simple.itemTopK")) {
                                    items.set(
                                            i,
                                            new MemoryOptionItemView(
                                                    item.key(),
                                                    21,
                                                    item.description(),
                                                    item.type(),
                                                    item.defaultValue(),
                                                    item.constraints()));
                                }
                            }
                        });
        return projection;
    }

    private static Memory closeTrackingMemory(AtomicInteger closeCount) {
        return new TestMemory(closeCount::incrementAndGet);
    }

    private static class InMemoryServerRuntimeConfigRepository
            implements ServerRuntimeConfigRepository {

        private ServerRuntimeConfigDO current;

        @Override
        public Optional<ServerRuntimeConfigDO> findActive(String configKey) {
            if (current == null || !configKey.equals(current.getConfigKey())) {
                return Optional.empty();
            }
            return Optional.of(copy(current));
        }

        @Override
        public void insertInitial(String configKey, long version, String configJson) {
            current = new ServerRuntimeConfigDO();
            current.setConfigKey(configKey);
            current.setConfigVersion(version);
            current.setConfigJson(configJson);
        }

        @Override
        public boolean update(String configKey, long expectedVersion, String configJson) {
            if (current == null
                    || !configKey.equals(current.getConfigKey())
                    || !Long.valueOf(expectedVersion).equals(current.getConfigVersion())) {
                return false;
            }
            current.setConfigVersion(expectedVersion + 1);
            current.setConfigJson(configJson);
            return true;
        }

        protected static ServerRuntimeConfigDO copy(ServerRuntimeConfigDO source) {
            ServerRuntimeConfigDO copy = new ServerRuntimeConfigDO();
            copy.setId(source.getId());
            copy.setConfigKey(source.getConfigKey());
            copy.setConfigVersion(source.getConfigVersion());
            copy.setConfigJson(source.getConfigJson());
            copy.setCreatedAt(source.getCreatedAt());
            copy.setUpdatedAt(source.getUpdatedAt());
            copy.setDeleted(source.getDeleted());
            return copy;
        }
    }

    private static final class FailingUpdateRepository
            extends InMemoryServerRuntimeConfigRepository {

        @Override
        public boolean update(String configKey, long expectedVersion, String configJson) {
            return false;
        }
    }
}
