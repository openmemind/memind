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

@TableName(value = "memory_thread_event", autoResultMap = true)
public class MemoryThreadEventDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String memoryId;
    private String threadKey;
    private String eventKey;
    private Long eventSeq;
    private String eventType;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant eventTime;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> eventPayloadJson;

    private Integer eventPayloadVersion;

    @TableField("is_meaningful")
    private Boolean meaningful;

    private Double confidence;

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

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public Long getEventSeq() {
        return eventSeq;
    }

    public void setEventSeq(Long eventSeq) {
        this.eventSeq = eventSeq;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public Map<String, Object> getEventPayloadJson() {
        return eventPayloadJson;
    }

    public void setEventPayloadJson(Map<String, Object> eventPayloadJson) {
        this.eventPayloadJson = eventPayloadJson;
    }

    public Integer getEventPayloadVersion() {
        return eventPayloadVersion;
    }

    public void setEventPayloadVersion(Integer eventPayloadVersion) {
        this.eventPayloadVersion = eventPayloadVersion;
    }

    public Boolean getMeaningful() {
        return meaningful;
    }

    public void setMeaningful(Boolean meaningful) {
        this.meaningful = meaningful;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
