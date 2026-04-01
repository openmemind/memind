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

import com.openmemind.ai.memory.server.domain.config.model.ServerRuntimeConfigDO;
import java.util.Optional;

public interface ServerRuntimeConfigRepository {

    Optional<ServerRuntimeConfigDO> findActive(String configKey);

    void insertInitial(String configKey, long version, String configJson);

    boolean update(String configKey, long expectedVersion, String configJson);
}
