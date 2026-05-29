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
package com.openmemind.ai.memory.server.service.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.server.domain.config.model.ServerRuntimeConfigDO;
import com.openmemind.ai.memory.server.domain.settings.UiPreferences;
import com.openmemind.ai.memory.server.service.config.ServerRuntimeConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class UiPreferenceServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getToleratesConcurrentInitialInsertRace() throws Exception {
        UiPreferences winner = new UiPreferences("30d", "grid", "dark", false, true);
        RacingInitialInsertRepository repository =
                new RacingInitialInsertRepository(UiPreferenceService.CONFIG_KEY, 4, write(winner));
        UiPreferenceService service = new UiPreferenceService(repository, objectMapper);

        UiPreferences preferences = service.get();

        assertThat(preferences).isEqualTo(winner);
        assertThat(repository.findActive(UiPreferenceService.CONFIG_KEY))
                .hasValueSatisfying(config -> assertThat(config.getConfigVersion()).isEqualTo(4));
    }

    @Test
    void updateAppliesRequestedPreferencesAfterConcurrentInitialInsertRace() throws Exception {
        UiPreferences winner = new UiPreferences("90d", "table", "system", true, false);
        UiPreferences requested = new UiPreferences("30d", "grid", "dark", false, true);
        RacingInitialInsertRepository repository =
                new RacingInitialInsertRepository(UiPreferenceService.CONFIG_KEY, 3, write(winner));
        UiPreferenceService service = new UiPreferenceService(repository, objectMapper);

        UiPreferences preferences = service.update(requested);

        assertThat(preferences).isEqualTo(requested);
        assertThat(repository.findActive(UiPreferenceService.CONFIG_KEY))
                .hasValueSatisfying(
                        config -> {
                            assertThat(config.getConfigVersion()).isEqualTo(4);
                            assertThat(read(config.getConfigJson())).isEqualTo(requested);
                        });
    }

    private UiPreferences read(String json) throws JacksonException {
        return objectMapper.readValue(json, UiPreferences.class);
    }

    private String write(UiPreferences preferences) throws JacksonException {
        return objectMapper.writeValueAsString(preferences);
    }

    private static final class RacingInitialInsertRepository
            implements ServerRuntimeConfigRepository {

        private final String winnerConfigKey;
        private final long winnerVersion;
        private final String winnerConfigJson;
        private boolean raced;
        private ServerRuntimeConfigDO current;

        private RacingInitialInsertRepository(
                String winnerConfigKey, long winnerVersion, String winnerConfigJson) {
            this.winnerConfigKey = winnerConfigKey;
            this.winnerVersion = winnerVersion;
            this.winnerConfigJson = winnerConfigJson;
        }

        @Override
        public Optional<ServerRuntimeConfigDO> findActive(String configKey) {
            if (current == null || !configKey.equals(current.getConfigKey())) {
                return Optional.empty();
            }
            return Optional.of(copy(current));
        }

        @Override
        public void insertInitial(String configKey, long version, String configJson) {
            if (!raced) {
                raced = true;
                current = config(winnerConfigKey, winnerVersion, winnerConfigJson);
                throw new DuplicateKeyException("simulated concurrent initial insert");
            }
            current = config(configKey, version, configJson);
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

        private static ServerRuntimeConfigDO config(
                String configKey, long version, String configJson) {
            ServerRuntimeConfigDO config = new ServerRuntimeConfigDO();
            config.setConfigKey(configKey);
            config.setConfigVersion(version);
            config.setConfigJson(configJson);
            return config;
        }

        private static ServerRuntimeConfigDO copy(ServerRuntimeConfigDO source) {
            ServerRuntimeConfigDO copy = new ServerRuntimeConfigDO();
            copy.setConfigKey(source.getConfigKey());
            copy.setConfigVersion(source.getConfigVersion());
            copy.setConfigJson(source.getConfigJson());
            return copy;
        }
    }
}
