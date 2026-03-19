package com.openmemind.ai.memory.plugin.store.mybatis.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.util.Map;

@TableName(value = "memory_raw_data", autoResultMap = true)
public class MemoryRawDataDO extends BaseDO {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String bizId;
    private String userId;
    private String agentId;
    private String memoryId;
    private String type;
    private String contentId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> segment;

    private String caption;
    private String captionVectorId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
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

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public Map<String, Object> getSegment() {
        return segment;
    }

    public void setSegment(Map<String, Object> segment) {
        this.segment = segment;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getCaptionVectorId() {
        return captionVectorId;
    }

    public void setCaptionVectorId(String captionVectorId) {
        this.captionVectorId = captionVectorId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
