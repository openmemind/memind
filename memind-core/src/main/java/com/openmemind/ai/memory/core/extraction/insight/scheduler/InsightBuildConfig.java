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
package com.openmemind.ai.memory.core.extraction.insight.scheduler;

/**
 * InsightBuildScheduler Configuration
 *
 * @param groupingThreshold  The threshold for the number of ungrouped items to trigger grouping
 * @param buildThreshold     The threshold for the number of unbuilt items within a group to trigger leaf building
 * @param concurrency        The maximum number of concurrent insight types
 * @param maxRetries         The number of retry attempts on failure
 */
public record InsightBuildConfig(
        int groupingThreshold, int buildThreshold, int concurrency, int maxRetries) {

    public static InsightBuildConfig defaults() {
        return new InsightBuildConfig(20, 10, 4, 2);
    }
}
