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
package com.openmemind.ai.memory.server.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.springframework.util.StringUtils;

public record AdminMemoryScope(
        String memoryId, String userId, String agentId, boolean agentScoped) {

    public static AdminMemoryScope fromMemoryId(String memoryId) {
        if (!StringUtils.hasText(memoryId)) {
            return new AdminMemoryScope(null, null, null, false);
        }
        String trimmed = memoryId.trim();
        int separator = trimmed.indexOf(':');
        if (separator < 0) {
            return new AdminMemoryScope(trimmed, trimmed, null, false);
        }
        String userId = trimmed.substring(0, separator);
        String agentId = trimmed.substring(separator + 1);
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(agentId)) {
            throw new IllegalArgumentException("memoryId must be user or user:agent");
        }
        return new AdminMemoryScope(trimmed, userId, agentId, true);
    }

    public boolean present() {
        return memoryId != null;
    }

    public String databaseAgentId() {
        return agentScoped ? agentId : "";
    }

    public <T> void applyToUserAgentQuery(
            LambdaQueryWrapper<T> wrapper,
            SFunction<T, ?> userColumn,
            SFunction<T, ?> agentColumn) {
        if (!present()) {
            return;
        }
        wrapper.eq(userColumn, userId);
        wrapper.eq(agentColumn, databaseAgentId());
    }

    public <T> void applyToUserAgentUpdate(
            LambdaUpdateWrapper<T> wrapper,
            SFunction<T, ?> userColumn,
            SFunction<T, ?> agentColumn) {
        if (!present()) {
            return;
        }
        wrapper.eq(userColumn, userId);
        wrapper.eq(agentColumn, databaseAgentId());
    }

    public <T> void applyToMemoryIdQuery(
            LambdaQueryWrapper<T> wrapper, SFunction<T, ?> memoryIdColumn) {
        if (present()) {
            wrapper.eq(memoryIdColumn, memoryId);
        }
    }

    public <T> void applyToMemoryIdUpdate(
            LambdaUpdateWrapper<T> wrapper, SFunction<T, ?> memoryIdColumn) {
        if (present()) {
            wrapper.eq(memoryIdColumn, memoryId);
        }
    }
}
