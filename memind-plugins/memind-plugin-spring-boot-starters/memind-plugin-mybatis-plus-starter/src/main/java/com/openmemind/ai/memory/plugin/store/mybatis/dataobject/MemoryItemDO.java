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

@TableName(value = "memory_item", autoResultMap = true)
public class MemoryItemDO extends BaseDO {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long bizId;
    private String userId;
    private String agentId;
    private String memoryId;
    private String content;
    private String scope;
    private String category;
    private String sourceClient;
    private String vectorId;
    private String rawDataId;
    private String contentHash;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant occurredAt;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant occurredStart;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant occurredEnd;

    private String timeGranularity;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant observedAt;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant temporalStart;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant temporalEndOrAnchor;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant temporalAnchor;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> metadata;

    private String type;

    private String rawDataType;
    private String extractionBatchId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSourceClient() {
        return sourceClient;
    }

    public void setSourceClient(String sourceClient) {
        this.sourceClient = sourceClient;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    public String getRawDataId() {
        return rawDataId;
    }

    public void setRawDataId(String rawDataId) {
        this.rawDataId = rawDataId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Instant getOccurredStart() {
        return occurredStart;
    }

    public void setOccurredStart(Instant occurredStart) {
        this.occurredStart = occurredStart;
    }

    public Instant getOccurredEnd() {
        return occurredEnd;
    }

    public void setOccurredEnd(Instant occurredEnd) {
        this.occurredEnd = occurredEnd;
    }

    public String getTimeGranularity() {
        return timeGranularity;
    }

    public void setTimeGranularity(String timeGranularity) {
        this.timeGranularity = timeGranularity;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public Instant getTemporalStart() {
        return temporalStart;
    }

    public void setTemporalStart(Instant temporalStart) {
        this.temporalStart = temporalStart;
    }

    public Instant getTemporalEndOrAnchor() {
        return temporalEndOrAnchor;
    }

    public void setTemporalEndOrAnchor(Instant temporalEndOrAnchor) {
        this.temporalEndOrAnchor = temporalEndOrAnchor;
    }

    public Instant getTemporalAnchor() {
        return temporalAnchor;
    }

    public void setTemporalAnchor(Instant temporalAnchor) {
        this.temporalAnchor = temporalAnchor;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRawDataType() {
        return rawDataType;
    }

    public void setRawDataType(String rawDataType) {
        this.rawDataType = rawDataType;
    }

    public String getExtractionBatchId() {
        return extractionBatchId;
    }

    public void setExtractionBatchId(String extractionBatchId) {
        this.extractionBatchId = extractionBatchId;
    }
}
