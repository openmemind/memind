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
import java.util.List;

final class RelationshipAnchorProvider implements ThreadAnchorProvider {

    @Override
    public List<ThreadIntakeSignal> extract(ThreadAnchorContext context) {
        List<ThreadIntakeSignal.SemanticMarker> parsedMarkers =
                ThreadAnchorProviderSupport.parseMarkers(context.semantics());
        List<Long> continuityTargetItemIds =
                ThreadAnchorProviderSupport.parseContinuityTargets(context.semantics());
        List<String> relationshipParticipants =
                ThreadAnchorProviderSupport.relationshipParticipants(context);
        if (!validRelationshipPair(relationshipParticipants)) {
            return List.of();
        }
        return List.of(
                new ThreadIntakeSignal(
                        context.triggerItem().memoryId(),
                        context.triggerItem().id(),
                        context.triggerItem().content(),
                        context.eventTime(),
                        MemoryThreadType.RELATIONSHIP,
                        List.of(
                                new ThreadIntakeSignal.AnchorCandidate(
                                        "relationship", null, relationshipParticipants, 1.0d)),
                        new ThreadIntakeSignal.ThreadEligibilityScore(
                                1.0d,
                                ThreadAnchorProviderSupport.relationshipContinuity(
                                        relationshipParticipants,
                                        context.adjacentLinks(),
                                        context.cooccurrences()),
                                ThreadAnchorProviderSupport.statefulness(context.triggerItem())),
                        continuityTargetItemIds,
                        ThreadAnchorProviderSupport.unboundMarkers(parsedMarkers),
                        List.of(),
                        0.95d));
    }

    private static boolean validRelationshipPair(List<String> participants) {
        if (participants.size() != 2) {
            return false;
        }
        long personCount =
                participants.stream().filter(token -> token.startsWith("person:")).count();
        long specialCount =
                participants.stream().filter(token -> token.startsWith("special:")).count();
        return personCount >= 1 && specialCount < 2;
    }
}
