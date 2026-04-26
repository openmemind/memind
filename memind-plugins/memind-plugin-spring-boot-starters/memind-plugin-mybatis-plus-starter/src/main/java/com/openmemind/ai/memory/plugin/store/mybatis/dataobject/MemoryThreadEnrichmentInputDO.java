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
import com.openmemind.ai.memory.plugin.store.mybatis.handler.InstantTypeHandler;
import java.time.Instant;
import java.util.Map;

@TableName(value = "memory_thread_enrichment_input", autoResultMap = true)
public class MemoryThreadEnrichmentInputDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String memoryId;
    private String threadKey;
    private String inputRunKey;
    private Integer entrySeq;
    private Long basisCutoffItemId;
    private Long basisMeaningfulEventCount;
    private String basisMaterializationPolicyVersion;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> payloadJson;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> provenanceJson;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getThreadKey() {
        return threadKey;
    }

    public void setThreadKey(String threadKey) {
        this.threadKey = threadKey;
    }

    public String getInputRunKey() {
        return inputRunKey;
    }

    public void setInputRunKey(String inputRunKey) {
        this.inputRunKey = inputRunKey;
    }

    public Integer getEntrySeq() {
        return entrySeq;
    }

    public void setEntrySeq(Integer entrySeq) {
        this.entrySeq = entrySeq;
    }

    public Long getBasisCutoffItemId() {
        return basisCutoffItemId;
    }

    public void setBasisCutoffItemId(Long basisCutoffItemId) {
        this.basisCutoffItemId = basisCutoffItemId;
    }

    public Long getBasisMeaningfulEventCount() {
        return basisMeaningfulEventCount;
    }

    public void setBasisMeaningfulEventCount(Long basisMeaningfulEventCount) {
        this.basisMeaningfulEventCount = basisMeaningfulEventCount;
    }

    public String getBasisMaterializationPolicyVersion() {
        return basisMaterializationPolicyVersion;
    }

    public void setBasisMaterializationPolicyVersion(String basisMaterializationPolicyVersion) {
        this.basisMaterializationPolicyVersion = basisMaterializationPolicyVersion;
    }

    public Map<String, Object> getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(Map<String, Object> payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Map<String, Object> getProvenanceJson() {
        return provenanceJson;
    }

    public void setProvenanceJson(Map<String, Object> provenanceJson) {
        this.provenanceJson = provenanceJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
