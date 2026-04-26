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

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.thread.marker.ThreadSemanticMarker;
import com.openmemind.ai.memory.core.extraction.thread.marker.ThreadSemanticMarkerReader;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure signal extraction from committed item and graph evidence.
 */
public final class ThreadIntakeSignalExtractor {

    private static final Logger log = LoggerFactory.getLogger(ThreadIntakeSignalExtractor.class);

    private final List<ThreadAnchorProvider> providers;
    private final ThreadDerivationMetrics metrics;

    public ThreadIntakeSignalExtractor(ThreadMaterializationPolicy policy) {
        this(policy, ThreadDerivationMetrics.NOOP);
    }

    ThreadIntakeSignalExtractor(
            ThreadMaterializationPolicy policy, ThreadDerivationMetrics metrics) {
        Objects.requireNonNull(policy, "policy");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.providers =
                List.of(
                        new RelationshipAnchorProvider(),
                        new GroupRelationshipAnchorProvider(),
                        new TopicAnchorProvider(),
                        new MetadataCanonicalRefAnchorProvider());
    }

    public List<ThreadIntakeSignal> extract(
            MemoryItem triggerItem,
            List<ItemEntityMention> mentions,
            List<ItemLink> adjacentLinks,
            List<EntityCooccurrence> cooccurrences) {
        Objects.requireNonNull(triggerItem, "triggerItem");
        List<ItemEntityMention> mentionRows = mentions == null ? List.of() : List.copyOf(mentions);
        List<ItemLink> linkRows = adjacentLinks == null ? List.of() : List.copyOf(adjacentLinks);
        List<EntityCooccurrence> cooccurrenceRows =
                cooccurrences == null ? List.of() : List.copyOf(cooccurrences);
        ThreadSemanticMarker.SemanticsEnvelope semantics =
                ThreadSemanticMarkerReader.read(triggerItem.metadata());

        Instant semanticTime = ThreadEventTimeResolver.resolve(triggerItem);
        if (semanticTime == null) {
            log.warn(
                    "Skipping thread signal extraction because item lacks authoritative time:"
                            + " memoryId={} itemId={}",
                    triggerItem.memoryId(),
                    triggerItem.id());
            return List.of();
        }
        ThreadAnchorContext context =
                new ThreadAnchorContext(
                        triggerItem,
                        mentionRows,
                        linkRows,
                        cooccurrenceRows,
                        semantics,
                        semanticTime);
        ArrayList<ThreadIntakeSignal> signals = new ArrayList<>();
        for (ThreadAnchorProvider provider : providers) {
            List<ThreadIntakeSignal> emitted = provider.extract(context);
            if (!emitted.isEmpty()) {
                metrics.onProviderHit(providerName(provider));
                signals.addAll(emitted);
            }
        }

        return List.copyOf(signals);
    }

    private static String providerName(ThreadAnchorProvider provider) {
        if (provider instanceof RelationshipAnchorProvider) {
            return "relationship";
        }
        if (provider instanceof GroupRelationshipAnchorProvider) {
            return "relationship_group";
        }
        if (provider instanceof TopicAnchorProvider) {
            return "topic";
        }
        if (provider instanceof MetadataCanonicalRefAnchorProvider) {
            return "metadata_canonical_ref";
        }
        return provider.getClass().getSimpleName();
    }
}
