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

import java.util.concurrent.TimeUnit;

/**
 * Replay publication statistics emitted after a successful atomic replay commit.
 *
 * <p>{@code triggerItemCount} is the number of outbox trigger items covered by the replay. For an
 * intake replay this is the claimed batch size; for a rebuild this is the number of pending outbox
 * triggers at or below the rebuild cutoff.
 *
 * <p>Loaded counts describe source rows read by the materializer. {@code loadedAdjacentLinkCount}
 * counts unique item-link rows after per-item adjacency grouping has been de-duplicated. Replaced
 * counts describe projection rows written by the successful commit.
 *
 * <p>{@code durationMillis} is measured from immediately before materialization starts until after
 * the successful projection commit returns and before replay success listeners run. It therefore
 * includes materialization and projection-store commit latency.
 */
public record ThreadReplayStats(
        ThreadReplayOrigin origin,
        long durationMillis,
        long cutoffItemId,
        int triggerItemCount,
        int loadedItemCount,
        int loadedMentionCount,
        int loadedAdjacentLinkCount,
        int loadedCooccurrenceCount,
        int replacedThreadCount,
        int replacedEventCount,
        int replacedMembershipCount) {

    static ThreadReplayStats from(
            ThreadReplayOrigin origin,
            long replayStartedNanos,
            long replayCutoffItemId,
            int triggerItemCount,
            ThreadProjectionMaterializer.MaterializedProjection projection) {
        return new ThreadReplayStats(
                origin,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - replayStartedNanos),
                replayCutoffItemId,
                triggerItemCount,
                projection.loadedItemCount(),
                projection.loadedMentionCount(),
                projection.loadedAdjacentLinkCount(),
                projection.loadedCooccurrenceCount(),
                projection.threads().size(),
                projection.events().size(),
                projection.memberships().size());
    }
}
