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

@TableName(value = "memory_thread", autoResultMap = true)
public class MemoryThreadDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long bizId;
    private String userId;
    private String agentId;
    private String memoryId;
    private String threadKey;
    private String episodeType;
    private String title;
    private String summarySnapshot;
    private String status;
    private Double confidence;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant startAt;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant endAt;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant lastActivityAt;

    private Long originItemId;
    private Long anchorItemId;
    private Integer displayOrderHint;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> metadata;

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

    public String getThreadKey() {
        return threadKey;
    }

    public void setThreadKey(String threadKey) {
        this.threadKey = threadKey;
    }

    public String getEpisodeType() {
        return episodeType;
    }

    public void setEpisodeType(String episodeType) {
        this.episodeType = episodeType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummarySnapshot() {
        return summarySnapshot;
    }

    public void setSummarySnapshot(String summarySnapshot) {
        this.summarySnapshot = summarySnapshot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public Long getOriginItemId() {
        return originItemId;
    }

    public void setOriginItemId(Long originItemId) {
        this.originItemId = originItemId;
    }

    public Long getAnchorItemId() {
        return anchorItemId;
    }

    public void setAnchorItemId(Long anchorItemId) {
        this.anchorItemId = anchorItemId;
    }

    public Integer getDisplayOrderHint() {
        return displayOrderHint;
    }

    public void setDisplayOrderHint(Integer displayOrderHint) {
        this.displayOrderHint = displayOrderHint;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
