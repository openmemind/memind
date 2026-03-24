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
        if (Objects.nonNull(metaObject)) {
            setFieldValByName("updatedAt", Instant.now(), metaObject);
        }
    }
}
