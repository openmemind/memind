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
package com.openmemind.ai.memory.plugin.store.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryGraphEntityDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.sql.GraphQuerySqlProvider;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.sql.ItemGraphMutationSqlProvider;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface MemoryGraphEntityMapper extends BaseMapper<MemoryGraphEntityDO> {

    @InsertProvider(type = ItemGraphMutationSqlProvider.class, method = "insertGraphEntities")
    int insertBatch(@Param("entities") List<MemoryGraphEntityDO> entities);

    @SelectProvider(type = GraphQuerySqlProvider.class, method = "selectEntitiesByKeys")
    @ResultMap("mybatis-plus_MemoryGraphEntityDO")
    List<MemoryGraphEntityDO> selectByEntityKeys(
            @Param("memoryId") String memoryId, @Param("entityKeys") Collection<String> entityKeys);
}
