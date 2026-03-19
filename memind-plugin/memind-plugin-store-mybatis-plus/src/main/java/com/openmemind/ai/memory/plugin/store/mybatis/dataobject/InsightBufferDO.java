package com.openmemind.ai.memory.plugin.store.mybatis.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Insight buffer data object (one BufferEntry per row)
 *
 */
@TableName(value = "memory_insight_buffer", autoResultMap = true)
public class InsightBufferDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String userId;
    private String agentId;
    private String memoryId;
    private String insightTypeName;
    private Long itemId;
    private String groupName;
    private Boolean built;

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

    public String getInsightTypeName() {
        return insightTypeName;
    }

    public void setInsightTypeName(String insightTypeName) {
        this.insightTypeName = insightTypeName;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Boolean getBuilt() {
        return built;
    }

    public void setBuilt(Boolean built) {
        this.built = built;
    }
}
