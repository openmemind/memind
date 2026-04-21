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
public class MemoryThreadProjectionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String memoryId;
    private String threadKey;
    private String threadType;
    private String anchorKind;
    private String anchorKey;
    private String displayLabel;
    private String lifecycleStatus;
    private String objectState;
    private String headline;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> snapshotJson;

    private Integer snapshotVersion;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant openedAt;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant lastEventAt;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant lastMeaningfulUpdateAt;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant closedAt;

    private Long eventCount;
    private Long memberCount;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant createdAt;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant updatedAt;

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

    public String getThreadType() {
        return threadType;
    }

    public void setThreadType(String threadType) {
        this.threadType = threadType;
    }

    public String getAnchorKind() {
        return anchorKind;
    }

    public void setAnchorKind(String anchorKind) {
        this.anchorKind = anchorKind;
    }

    public String getAnchorKey() {
        return anchorKey;
    }

    public void setAnchorKey(String anchorKey) {
        this.anchorKey = anchorKey;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getObjectState() {
        return objectState;
    }

    public void setObjectState(String objectState) {
        this.objectState = objectState;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public Map<String, Object> getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(Map<String, Object> snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public Integer getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(Integer snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(Instant lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public Instant getLastMeaningfulUpdateAt() {
        return lastMeaningfulUpdateAt;
    }

    public void setLastMeaningfulUpdateAt(Instant lastMeaningfulUpdateAt) {
        this.lastMeaningfulUpdateAt = lastMeaningfulUpdateAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Long getEventCount() {
        return eventCount;
    }

    public void setEventCount(Long eventCount) {
        this.eventCount = eventCount;
    }

    public Long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Long memberCount) {
        this.memberCount = memberCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
