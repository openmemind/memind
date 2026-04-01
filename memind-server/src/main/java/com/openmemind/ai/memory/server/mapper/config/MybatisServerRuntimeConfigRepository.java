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
package com.openmemind.ai.memory.server.mapper.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.openmemind.ai.memory.server.domain.config.model.ServerRuntimeConfigDO;
import com.openmemind.ai.memory.server.service.config.ServerRuntimeConfigRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisServerRuntimeConfigRepository implements ServerRuntimeConfigRepository {

    private final ServerRuntimeConfigMapper mapper;

    public MybatisServerRuntimeConfigRepository(ServerRuntimeConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ServerRuntimeConfigDO> findActive(String configKey) {
        return Optional.ofNullable(
                mapper.selectOne(
                        new LambdaQueryWrapper<ServerRuntimeConfigDO>()
                                .eq(ServerRuntimeConfigDO::getConfigKey, configKey)));
    }

    @Override
    public void insertInitial(String configKey, long version, String configJson) {
        ServerRuntimeConfigDO dataObject = new ServerRuntimeConfigDO();
        dataObject.setConfigKey(configKey);
        dataObject.setConfigVersion(version);
        dataObject.setConfigJson(configJson);
        mapper.insert(dataObject);
    }

    @Override
    public boolean update(String configKey, long expectedVersion, String configJson) {
        return mapper.update(
                        null,
                        new LambdaUpdateWrapper<ServerRuntimeConfigDO>()
                                .eq(ServerRuntimeConfigDO::getConfigKey, configKey)
                                .eq(ServerRuntimeConfigDO::getConfigVersion, expectedVersion)
                                .set(ServerRuntimeConfigDO::getConfigJson, configJson)
                                .set(ServerRuntimeConfigDO::getConfigVersion, expectedVersion + 1))
                > 0;
    }
}
