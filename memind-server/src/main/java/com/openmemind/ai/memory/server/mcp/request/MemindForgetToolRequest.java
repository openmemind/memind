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
package com.openmemind.ai.memory.server.mcp.request;

import com.openmemind.ai.memory.server.mcp.support.MemindMcpToolValidation;
import java.util.List;
import java.util.Locale;

public record MemindForgetToolRequest(
        String userId,
        String agentId,
        TargetType targetType,
        List<String> ids,
        String reason,
        boolean dryRun) {

    public static MemindForgetToolRequest of(
            String userId,
            String agentId,
            String targetType,
            List<String> ids,
            String reason,
            Boolean dryRun,
            int maxIdsPerRequest) {
        return new MemindForgetToolRequest(
                MemindMcpToolValidation.requireText(userId, "userId"),
                MemindMcpToolValidation.requireText(agentId, "agentId"),
                parseTargetType(targetType),
                MemindMcpToolValidation.requireStringIds(ids, "ids", maxIdsPerRequest),
                MemindMcpToolValidation.requireText(reason, "reason"),
                dryRun == null || dryRun);
    }

    public List<Long> longIds(int maxIdsPerRequest) {
        return MemindMcpToolValidation.requireLongIds(ids, "ids", maxIdsPerRequest);
    }

    private static TargetType parseTargetType(String value) {
        String normalized =
                MemindMcpToolValidation.requireText(value, "targetType").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ITEM" -> TargetType.ITEM;
            case "RAWDATA" -> TargetType.RAWDATA;
            default ->
                    throw new IllegalArgumentException("targetType must be one of ITEM, RAWDATA");
        };
    }

    public enum TargetType {
        ITEM,
        RAWDATA
    }
}
