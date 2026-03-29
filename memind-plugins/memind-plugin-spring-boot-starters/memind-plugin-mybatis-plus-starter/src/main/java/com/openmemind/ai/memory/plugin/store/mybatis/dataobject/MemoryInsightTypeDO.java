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
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeConfig;
import com.openmemind.ai.memory.plugin.store.mybatis.handler.InstantTypeHandler;
import java.time.Instant;
import java.util.List;

@TableName(value = "memory_insight_type", autoResultMap = true)
public class MemoryInsightTypeDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long bizId;

    private String name;
    private String description;
    private String descriptionVectorId;
    private Integer targetTokens;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> categories;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant lastUpdatedAt;

    private String analysisMode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private InsightTreeConfig treeConfig;

    private String scope;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescriptionVectorId() {
        return descriptionVectorId;
    }

    public void setDescriptionVectorId(String descriptionVectorId) {
        this.descriptionVectorId = descriptionVectorId;
    }

    public Integer getTargetTokens() {
        return targetTokens;
    }

    public void setTargetTokens(Integer targetTokens) {
        this.targetTokens = targetTokens;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(Instant lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public String getAnalysisMode() {
        return analysisMode;
    }

    public void setAnalysisMode(String analysisMode) {
        this.analysisMode = analysisMode;
    }

    public InsightTreeConfig getTreeConfig() {
        return treeConfig;
    }

    public void setTreeConfig(InsightTreeConfig treeConfig) {
        this.treeConfig = treeConfig;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
