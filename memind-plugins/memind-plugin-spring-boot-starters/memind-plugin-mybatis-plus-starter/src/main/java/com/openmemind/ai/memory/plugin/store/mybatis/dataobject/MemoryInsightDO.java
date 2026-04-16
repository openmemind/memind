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
import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.plugin.store.mybatis.handler.InstantTypeHandler;
import java.time.Instant;
import java.util.List;

@TableName(value = "memory_insight", autoResultMap = true)
public class MemoryInsightDO extends BaseDO {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long bizId;
    private String userId;
    private String agentId;
    private String memoryId;
    private String type;
    private String scope;
    private String name;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> categories;

    private String content;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<InsightPoint> points;

    private String groupName;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant lastReasonedAt;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Float> summaryEmbedding;

    // === Tree structure fields ===
    private String tier;
    private Long parentInsightId;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Long> childInsightIds;

    private Integer version;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public List<InsightPoint> getPoints() {
        return points;
    }

    public void setPoints(List<InsightPoint> points) {
        this.points = points;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Instant getLastReasonedAt() {
        return lastReasonedAt;
    }

    public void setLastReasonedAt(Instant lastReasonedAt) {
        this.lastReasonedAt = lastReasonedAt;
    }

    public List<Float> getSummaryEmbedding() {
        return summaryEmbedding;
    }

    public void setSummaryEmbedding(List<Float> summaryEmbedding) {
        this.summaryEmbedding = summaryEmbedding;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public Long getParentInsightId() {
        return parentInsightId;
    }

    public void setParentInsightId(Long parentInsightId) {
        this.parentInsightId = parentInsightId;
    }

    public List<Long> getChildInsightIds() {
        return childInsightIds;
    }

    public void setChildInsightIds(List<Long> childInsightIds) {
        this.childInsightIds = childInsightIds;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
