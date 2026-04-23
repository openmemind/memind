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
import java.util.Comparator;
import java.util.List;

final class MetadataCanonicalRefAnchorProvider implements ThreadAnchorProvider {

    @Override
    public List<ThreadIntakeSignal> extract(ThreadAnchorContext context) {
        List<ThreadIntakeSignal.SemanticMarker> parsedMarkers =
                ThreadAnchorProviderSupport.parseMarkers(context.semantics());
        List<Long> continuityTargetItemIds =
                ThreadAnchorProviderSupport.parseContinuityTargets(context.semantics());
        ArrayList<ThreadIntakeSignal> signals = new ArrayList<>();

        ThreadAnchorProviderSupport.metadataAnchorCandidates(context.semantics().canonicalRefs())
                .stream()
                .sorted(
                        Comparator.comparing(
                                        ThreadAnchorProviderSupport.MetadataAnchorCandidate::threadType)
                                .thenComparing(
                                        ThreadAnchorProviderSupport.MetadataAnchorCandidate::anchorKind)
                                .thenComparing(
                                        ThreadAnchorProviderSupport.MetadataAnchorCandidate::anchorKey))
                .forEach(
                        candidate -> {
                            List<ThreadIntakeSignal.SemanticMarker> relevantMarkers =
                                    ThreadAnchorProviderSupport.markersForAnchor(
                                            parsedMarkers,
                                            candidate.threadType(),
                                            candidate.anchorKind(),
                                            candidate.anchorKey());
                            boolean boundMeaningfulMarker =
                                    ThreadAnchorProviderSupport.hasBoundMeaningfulMarker(
                                            parsedMarkers,
                                            candidate.threadType(),
                                            candidate.anchorKind(),
                                            candidate.anchorKey());
                            ThreadIntakeSignal.ThreadEligibilityScore eligibility =
                                    ThreadAnchorProviderSupport.metadataEligibility(
                                            context.triggerItem(),
                                            candidate.threadType(),
                                            context.adjacentLinks(),
                                            context.cooccurrences(),
                                            boundMeaningfulMarker);
                            signals.add(
                                    new ThreadIntakeSignal(
                                            context.triggerItem().memoryId(),
                                            context.triggerItem().id(),
                                            context.triggerItem().content(),
                                            context.eventTime(),
                                            candidate.threadType(),
                                            List.of(
                                                    new ThreadIntakeSignal.AnchorCandidate(
                                                            candidate.anchorKind(),
                                                            candidate.anchorKey(),
                                                            List.of(),
                                                            1.0d)),
                                            eligibility,
                                            continuityTargetItemIds,
                                            relevantMarkers,
                                            List.of(candidate.rawCanonicalRef()),
                                            0.92d));
                        });

        return List.copyOf(signals);
    }
}
