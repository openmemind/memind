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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import java.util.ArrayList;
import java.util.List;

final class TopicAnchorProvider implements ThreadAnchorProvider {

    @Override
    public List<ThreadIntakeSignal> extract(ThreadAnchorContext context) {
        List<ThreadIntakeSignal.SemanticMarker> parsedMarkers =
                ThreadAnchorProviderSupport.parseMarkers(context.semantics());
        List<Long> continuityTargetItemIds =
                ThreadAnchorProviderSupport.parseContinuityTargets(context.semantics());
        ArrayList<ThreadIntakeSignal> signals = new ArrayList<>();

        ThreadAnchorProviderSupport.normalizedEntityKeys(context).stream()
                .filter(entityKey -> entityKey.startsWith("concept:"))
                .sorted()
                .forEach(
                        conceptKey ->
                                signals.add(
                                        new ThreadIntakeSignal(
                                                context.triggerItem().memoryId(),
                                                context.triggerItem().id(),
                                                context.triggerItem().content(),
                                                context.eventTime(),
                                                MemoryThreadType.TOPIC,
                                                List.of(
                                                        new ThreadIntakeSignal.AnchorCandidate(
                                                                "topic",
                                                                conceptKey,
                                                                List.of(),
                                                                0.80d)),
                                                new ThreadIntakeSignal.ThreadEligibilityScore(
                                                        0.72d,
                                                        ThreadAnchorProviderSupport.topicContinuity(
                                                                context.adjacentLinks(),
                                                                context.cooccurrences()),
                                                        ThreadAnchorProviderSupport.statefulness(
                                                                context.triggerItem())),
                                                continuityTargetItemIds,
                                                ThreadAnchorProviderSupport.markersForAnchor(
                                                        parsedMarkers,
                                                        MemoryThreadType.TOPIC,
                                                        "topic",
                                                        conceptKey),
                                                List.of(),
                                                0.88d)));

        return List.copyOf(signals);
    }
}
