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
package com.openmemind.ai.memory.server.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memind.mcp")
public record MemindMcpToolProperties(
        boolean governanceEnabled,
        int defaultTokenBudget,
        int maxTokenBudget,
        int maxItemsPerContext,
        int maxResultLimit,
        int maxIdsPerRequest,
        int maxRawSegmentChars) {

    public MemindMcpToolProperties {
        if (defaultTokenBudget <= 0) {
            defaultTokenBudget = 1800;
        }
        if (maxTokenBudget <= 0) {
            maxTokenBudget = 6000;
        }
        if (maxItemsPerContext <= 0) {
            maxItemsPerContext = 50;
        }
        if (maxResultLimit <= 0) {
            maxResultLimit = 100;
        }
        if (maxIdsPerRequest <= 0) {
            maxIdsPerRequest = 50;
        }
        if (maxRawSegmentChars <= 0) {
            maxRawSegmentChars = 12000;
        }
    }
}
