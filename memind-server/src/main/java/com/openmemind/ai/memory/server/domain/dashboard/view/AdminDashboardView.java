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
package com.openmemind.ai.memory.server.domain.dashboard.view;

import java.util.List;

public record AdminDashboardView(
        Totals totals,
        Backlog backlog,
        Activity activity,
        Breakdown breakdown,
        HealthSignals healthSignals) {

    public record Totals(
            long rawData,
            long items,
            long insights,
            long memoryThreads,
            long graphEntities,
            long itemLinks) {}

    public record Backlog(
            long conversationPending,
            long insightUnbuilt,
            long insightUngrouped,
            long threadOutboxPending,
            long threadOutboxFailed,
            long graphBatchRepairRequired) {}

    public record Activity(
            int days,
            List<DailyCount> rawDataCreated,
            List<DailyCount> itemsCreated,
            List<DailyCount> insightsCreated) {}

    public record DailyCount(String date, long count) {}

    public record Breakdown(
            List<NamedCount> sourceClients,
            List<NamedCount> rawDataTypes,
            List<NamedCount> itemTypes,
            List<NamedCount> insightTypes,
            List<NamedCount> graphLinkTypes) {}

    public record NamedCount(String name, long count) {}

    public record StateCount(String state, long count) {}

    public record HealthSignals(
            boolean graphEnabled,
            boolean retrievalGraphAssistEnabled,
            List<StateCount> threadProjectionStates) {}
}
