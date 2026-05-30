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
package com.openmemind.ai.memory.server.mcp.response;

import com.openmemind.ai.memory.server.mcp.request.MemindForgetToolRequest.TargetType;
import java.util.List;

public record MemindForgetResponse(
        String status,
        TargetType targetType,
        boolean dryRun,
        List<String> deleted,
        List<String> notFound,
        List<String> blocked) {

    public MemindForgetResponse {
        deleted = deleted == null ? List.of() : List.copyOf(deleted);
        notFound = notFound == null ? List.of() : List.copyOf(notFound);
        blocked = blocked == null ? List.of() : List.copyOf(blocked);
    }

    public static MemindForgetResponse dryRun(
            TargetType targetType, List<String> wouldDelete, List<String> notFound) {
        return new MemindForgetResponse(
                "DRY_RUN",
                targetType,
                true,
                List.of(),
                List.copyOf(notFound),
                List.copyOf(wouldDelete));
    }

    public static MemindForgetResponse deleted(
            TargetType targetType, List<String> deleted, List<String> notFound) {
        return new MemindForgetResponse(
                "DELETED",
                targetType,
                false,
                List.copyOf(deleted),
                List.copyOf(notFound),
                List.of());
    }
}
