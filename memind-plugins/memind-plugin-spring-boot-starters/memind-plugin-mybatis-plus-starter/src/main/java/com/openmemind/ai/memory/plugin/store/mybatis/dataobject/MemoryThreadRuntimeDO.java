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
import com.openmemind.ai.memory.plugin.store.mybatis.handler.InstantTypeHandler;
import java.time.Instant;

@TableName(value = "memory_thread_runtime", autoResultMap = true)
public class MemoryThreadRuntimeDO {

    @TableId(value = "memory_id", type = IdType.INPUT)
    private String memoryId;

    private String projectionState;
    private Long pendingCount;
    private Long failedCount;
    private Long lastEnqueuedItemId;
    private Long lastProcessedItemId;

    @TableField("rebuild_in_progress")
    private Boolean rebuildInProgress;

    private Long rebuildCutoffItemId;
    private Long rebuildEpoch;
    private String materializationPolicyVersion;
    private String invalidationReason;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant updatedAt;

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getProjectionState() {
        return projectionState;
    }

    public void setProjectionState(String projectionState) {
        this.projectionState = projectionState;
    }

    public Long getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(Long pendingCount) {
        this.pendingCount = pendingCount;
    }

    public Long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Long failedCount) {
        this.failedCount = failedCount;
    }

    public Long getLastEnqueuedItemId() {
        return lastEnqueuedItemId;
    }

    public void setLastEnqueuedItemId(Long lastEnqueuedItemId) {
        this.lastEnqueuedItemId = lastEnqueuedItemId;
    }

    public Long getLastProcessedItemId() {
        return lastProcessedItemId;
    }

    public void setLastProcessedItemId(Long lastProcessedItemId) {
        this.lastProcessedItemId = lastProcessedItemId;
    }

    public Boolean getRebuildInProgress() {
        return rebuildInProgress;
    }

    public void setRebuildInProgress(Boolean rebuildInProgress) {
        this.rebuildInProgress = rebuildInProgress;
    }

    public Long getRebuildCutoffItemId() {
        return rebuildCutoffItemId;
    }

    public void setRebuildCutoffItemId(Long rebuildCutoffItemId) {
        this.rebuildCutoffItemId = rebuildCutoffItemId;
    }

    public Long getRebuildEpoch() {
        return rebuildEpoch;
    }

    public void setRebuildEpoch(Long rebuildEpoch) {
        this.rebuildEpoch = rebuildEpoch;
    }

    public String getMaterializationPolicyVersion() {
        return materializationPolicyVersion;
    }

    public void setMaterializationPolicyVersion(String materializationPolicyVersion) {
        this.materializationPolicyVersion = materializationPolicyVersion;
    }

    public String getInvalidationReason() {
        return invalidationReason;
    }

    public void setInvalidationReason(String invalidationReason) {
        this.invalidationReason = invalidationReason;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
