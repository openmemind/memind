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
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemEntityMentionDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.sql.GraphQuerySqlProvider;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialect;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface MemoryItemEntityMentionMapper extends BaseMapper<MemoryItemEntityMentionDO> {

    @SelectProvider(type = GraphQuerySqlProvider.class, method = "selectMentionsByItemIds")
    @ResultMap("mybatis-plus_MemoryItemEntityMentionDO")
    List<MemoryItemEntityMentionDO> selectByItemIds(
            @Param("memoryId") String memoryId, @Param("itemIds") Collection<Long> itemIds);

    @SelectProvider(
            type = GraphQuerySqlProvider.class,
            method = "selectMentionsByEntityKeysLimited")
    @ResultMap("mybatis-plus_MemoryItemEntityMentionDO")
    List<MemoryItemEntityMentionDO> selectByEntityKeysLimited(
            @Param("memoryId") String memoryId,
            @Param("entityKeys") Collection<String> entityKeys,
            @Param("perEntityLimitPlusOne") int perEntityLimitPlusOne,
            @Param("dialect") DatabaseDialect dialect);
}
