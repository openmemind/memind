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
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryEntityCooccurrenceDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.sql.GraphQuerySqlProvider;
import java.util.Collection;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryEntityCooccurrenceMapper extends BaseMapper<MemoryEntityCooccurrenceDO> {

    @DeleteProvider(type = GraphQuerySqlProvider.class, method = "deleteCooccurrencesByEntityKeys")
    int deleteByEntityKeys(
            @Param("memoryId") String memoryId, @Param("entityKeys") Collection<String> entityKeys);
}
