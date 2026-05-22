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

import com.openmemind.ai.memory.core.utils.JsonUtils;
import com.openmemind.ai.memory.server.domain.config.model.ServerRuntimeConfigDO;
import com.openmemind.ai.memory.server.domain.settings.UiPreferences;
import com.openmemind.ai.memory.server.service.config.ServerRuntimeConfigRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class UiPreferenceService {

    public static final String CONFIG_KEY = "ui_preferences";

    private final ServerRuntimeConfigRepository repository;
    private final ObjectMapper objectMapper;

    public UiPreferenceService(
            ServerRuntimeConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper =
                objectMapper == null ? JsonUtils.newMapper() : objectMapper.rebuild().build();
    }

    public UiPreferences get() {
        return repository
                .findActive(CONFIG_KEY)
                .map(config -> read(config.getConfigJson()))
                .orElseGet(this::initializeDefaults);
    }

    public UiPreferences update(UiPreferences preferences) {
        UiPreferences requested = preferences == null ? UiPreferences.defaults() : preferences;
        return repository
                .findActive(CONFIG_KEY)
                .map(config -> updateExisting(config, requested))
                .orElseGet(() -> insertInitial(requested));
    }

    private UiPreferences initializeDefaults() {
        return insertInitial(UiPreferences.defaults());
    }

    private UiPreferences insertInitial(UiPreferences preferences) {
        repository.insertInitial(CONFIG_KEY, 1L, write(preferences));
        return preferences;
    }

    private UiPreferences updateExisting(ServerRuntimeConfigDO config, UiPreferences preferences) {
        long version = config.getConfigVersion() == null ? 1L : config.getConfigVersion();
        if (!repository.update(CONFIG_KEY, version, write(preferences))) {
            throw new OptimisticLockingFailureException(
                    "UI preferences version conflict, expected " + version);
        }
        return preferences;
    }

    private UiPreferences read(String json) {
        try {
            UiPreferences preferences = objectMapper.readValue(json, UiPreferences.class);
            return preferences == null ? UiPreferences.defaults() : preferences;
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize UI preferences", e);
        }
    }

    private String write(UiPreferences preferences) {
        try {
            return objectMapper.writeValueAsString(preferences);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize UI preferences", e);
        }
    }
}
