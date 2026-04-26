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
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.sql.ItemGraphMutationSqlProvider;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.sql.MemoryItemQuerySqlProvider;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialect;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface MemoryItemMapper extends BaseMapper<MemoryItemDO> {

    @InsertProvider(type = ItemGraphMutationSqlProvider.class, method = "insertMemoryItems")
    int insertBatch(@Param("items") List<MemoryItemDO> items);

    @SelectProvider(
            type = MemoryItemQuerySqlProvider.class,
            method = "selectTemporalOverlapCandidates")
    List<MemoryItemDO> selectTemporalOverlapCandidates(
            @Param("dialect") DatabaseDialect dialect,
            @Param("memoryId") String memoryId,
            @Param("itemType") String itemType,
            @Param("category") String category,
            @Param("excludeItemIds") Collection<Long> excludeItemIds,
            @Param("sourceStart") Instant sourceStart,
            @Param("sourceEndOrAnchor") Instant sourceEndOrAnchor,
            @Param("sourceAnchor") Instant sourceAnchor,
            @Param("limit") int limit);

    @SelectProvider(
            type = MemoryItemQuerySqlProvider.class,
            method = "selectTemporalBeforeCandidates")
    List<MemoryItemDO> selectTemporalBeforeCandidates(
            @Param("dialect") DatabaseDialect dialect,
            @Param("memoryId") String memoryId,
            @Param("itemType") String itemType,
            @Param("category") String category,
            @Param("excludeItemIds") Collection<Long> excludeItemIds,
            @Param("sourceAnchor") Instant sourceAnchor,
            @Param("limit") int limit);

    @SelectProvider(
            type = MemoryItemQuerySqlProvider.class,
            method = "selectTemporalAfterCandidates")
    List<MemoryItemDO> selectTemporalAfterCandidates(
            @Param("dialect") DatabaseDialect dialect,
            @Param("memoryId") String memoryId,
            @Param("itemType") String itemType,
            @Param("category") String category,
            @Param("excludeItemIds") Collection<Long> excludeItemIds,
            @Param("sourceAnchor") Instant sourceAnchor,
            @Param("limit") int limit);
}
