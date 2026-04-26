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
package com.openmemind.ai.memory.plugin.store.mybatis.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import java.util.Map;

@TableName(value = "memory_entity_cooccurrence", autoResultMap = true)
public class MemoryEntityCooccurrenceDO extends BaseDO {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String userId;
    private String agentId;
    private String memoryId;
    private String leftEntityKey;
    private String rightEntityKey;
    private Integer cooccurrenceCount;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> metadata;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getLeftEntityKey() {
        return leftEntityKey;
    }

    public void setLeftEntityKey(String leftEntityKey) {
        this.leftEntityKey = leftEntityKey;
    }

    public String getRightEntityKey() {
        return rightEntityKey;
    }

    public void setRightEntityKey(String rightEntityKey) {
        this.rightEntityKey = rightEntityKey;
    }

    public Integer getCooccurrenceCount() {
        return cooccurrenceCount;
    }

    public void setCooccurrenceCount(Integer cooccurrenceCount) {
        this.cooccurrenceCount = cooccurrenceCount;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
