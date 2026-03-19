package com.openmemind.ai.memory.plugin.store.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.BaseDO;
import java.time.Instant;
import java.util.Objects;
import org.apache.ibatis.reflection.MetaObject;

/**
 * Generic parameter filling implementation class
 * If there is no explicit assignment of generic parameters, this will fill and assign the generic parameters
 */
public class DefaultDBFieldHandler implements MetaObjectHandler {

    @Override
    @SuppressWarnings("PatternVariableCanBeUsed")
    public void insertFill(MetaObject metaObject) {
        if (Objects.nonNull(metaObject) && metaObject.getOriginalObject() instanceof BaseDO) {
            BaseDO baseDO = (BaseDO) metaObject.getOriginalObject();

            Instant current = Instant.now();
            // If creation time is null, use the current time as the insertion time
            if (Objects.isNull(baseDO.getCreatedAt())) {
                baseDO.setCreatedAt(current);
            }
            // If update time is null, use the current time as the update time
            if (Objects.isNull(baseDO.getUpdatedAt())) {
                baseDO.setUpdatedAt(current);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // If update time is null, use the current time as the update time
        Object modifyTime = getFieldValByName("updatedAt", metaObject);
        if (Objects.isNull(modifyTime)) {
            setFieldValByName("updatedAt", Instant.now(), metaObject);
        }
    }
}
