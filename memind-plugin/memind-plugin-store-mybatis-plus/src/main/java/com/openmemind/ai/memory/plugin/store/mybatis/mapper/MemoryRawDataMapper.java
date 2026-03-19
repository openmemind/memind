package com.openmemind.ai.memory.plugin.store.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryRawDataDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemoryRawDataMapper extends BaseMapper<MemoryRawDataDO> {}
