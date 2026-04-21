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
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import java.time.Instant;

@TableName(value = "memory_graph_alias_batch_receipt", autoResultMap = true)
public class MemoryGraphAliasBatchReceiptDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String userId;
    private String agentId;
    private String memoryId;
    private String entityKey;
    private String entityType;
    private String normalizedAlias;
    private String extractionBatchId;

    public static MemoryGraphAliasBatchReceiptDO from(
            MemoryId memoryId, ExtractionBatchId extractionBatchId, GraphEntityAlias alias) {
        MemoryGraphAliasBatchReceiptDO dataObject = new MemoryGraphAliasBatchReceiptDO();
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setEntityKey(alias.entityKey());
        dataObject.setEntityType(alias.entityType().name());
        dataObject.setNormalizedAlias(alias.normalizedAlias());
        dataObject.setExtractionBatchId(extractionBatchId.value());
        dataObject.setCreatedAt(Instant.now());
        dataObject.setUpdatedAt(Instant.now());
        return dataObject;
    }

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

    public String getEntityKey() {
        return entityKey;
    }

    public void setEntityKey(String entityKey) {
        this.entityKey = entityKey;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getNormalizedAlias() {
        return normalizedAlias;
    }

    public void setNormalizedAlias(String normalizedAlias) {
        this.normalizedAlias = normalizedAlias;
    }

    public String getExtractionBatchId() {
        return extractionBatchId;
    }

    public void setExtractionBatchId(String extractionBatchId) {
        this.extractionBatchId = extractionBatchId;
    }
}
