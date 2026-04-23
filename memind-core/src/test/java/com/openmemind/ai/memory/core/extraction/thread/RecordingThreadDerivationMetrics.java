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
package com.openmemind.ai.memory.core.extraction.thread;

import java.util.ArrayList;
import java.util.List;

final class RecordingThreadDerivationMetrics implements ThreadDerivationMetrics {

    private final List<Integer> claimedBatchSizes = new ArrayList<>();
    private final List<Integer> coalescedReplayCutoffCounts = new ArrayList<>();
    private final List<ThreadReplayOrigin> replayOrigins = new ArrayList<>();
    private final List<String> providerHits = new ArrayList<>();
    private final List<String> nonAdmissions = new ArrayList<>();
    private int wakeScheduledCount;
    private int wakeSubmissionFailedCount;
    private int groupRelationshipPublishedCount;

    @Override
    public void onWakeScheduled() {
        wakeScheduledCount++;
    }

    @Override
    public void onWakeSubmissionFailed() {
        wakeSubmissionFailedCount++;
    }

    @Override
    public void onClaimedBatch(int batchSize) {
        claimedBatchSizes.add(batchSize);
    }

    @Override
    public void onCoalescedReplayCutoffs(int coveredCutoffCount) {
        coalescedReplayCutoffCounts.add(coveredCutoffCount);
    }

    @Override
    public void onReplayPublished(ThreadReplayOrigin origin) {
        replayOrigins.add(origin);
    }

    @Override
    public void onProviderHit(String providerName) {
        providerHits.add(providerName);
    }

    @Override
    public void onGroupRelationshipPublished() {
        groupRelationshipPublishedCount++;
    }

    @Override
    public void onNonAdmission(ThreadNonAdmissionDisposition disposition, String reason) {
        nonAdmissions.add(disposition + ":" + reason);
    }

    List<Integer> claimedBatchSizes() {
        return List.copyOf(claimedBatchSizes);
    }

    List<Integer> coalescedReplayCutoffCounts() {
        return List.copyOf(coalescedReplayCutoffCounts);
    }

    List<ThreadReplayOrigin> replayOrigins() {
        return List.copyOf(replayOrigins);
    }

    List<String> providerHits() {
        return List.copyOf(providerHits);
    }

    List<String> nonAdmissions() {
        return List.copyOf(nonAdmissions);
    }

    int wakeScheduledCount() {
        return wakeScheduledCount;
    }

    int wakeSubmissionFailedCount() {
        return wakeSubmissionFailedCount;
    }

    int groupRelationshipPublishedCount() {
        return groupRelationshipPublishedCount;
    }
}
