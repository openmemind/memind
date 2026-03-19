package com.openmemind.ai.memory.plugin.store.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemoryItemMapper extends BaseMapper<MemoryItemDO> {}
