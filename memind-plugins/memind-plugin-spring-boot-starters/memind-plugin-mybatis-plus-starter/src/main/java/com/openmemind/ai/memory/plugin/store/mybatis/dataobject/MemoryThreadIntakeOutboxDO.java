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

@TableName(value = "thread_intake_outbox", autoResultMap = true)
public class MemoryThreadIntakeOutboxDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String memoryId;
    private Long triggerItemId;
    private String status;
    private Integer attemptCount;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant claimedAt;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant leaseExpiresAt;

    private String failureReason;
    private Long lastProcessedItemId;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant enqueuedAt;

    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant finalizedAt;

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

    public Long getTriggerItemId() {
        return triggerItemId;
    }

    public void setTriggerItemId(Long triggerItemId) {
        this.triggerItemId = triggerItemId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(Instant leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Long getLastProcessedItemId() {
        return lastProcessedItemId;
    }

    public void setLastProcessedItemId(Long lastProcessedItemId) {
        this.lastProcessedItemId = lastProcessedItemId;
    }

    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    public void setEnqueuedAt(Instant enqueuedAt) {
        this.enqueuedAt = enqueuedAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
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
