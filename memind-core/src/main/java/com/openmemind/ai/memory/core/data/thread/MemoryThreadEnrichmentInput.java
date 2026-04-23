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
package com.openmemind.ai.memory.core.data.thread;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Authoritative write-side input accepted for replay-safe thread enrichment.
 */
public record MemoryThreadEnrichmentInput(
        String memoryId,
        String threadKey,
        String inputRunKey,
        int entrySeq,
        long basisCutoffItemId,
        long basisMeaningfulEventCount,
        String basisMaterializationPolicyVersion,
        Map<String, Object> payloadJson,
        Map<String, Object> provenanceJson,
        Instant createdAt) {

    public MemoryThreadEnrichmentInput {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(threadKey, "threadKey");
        Objects.requireNonNull(inputRunKey, "inputRunKey");
        Objects.requireNonNull(basisMaterializationPolicyVersion, "basisMaterializationPolicyVersion");
        payloadJson = payloadJson == null ? Map.of() : Map.copyOf(payloadJson);
        provenanceJson = provenanceJson == null ? Map.of() : Map.copyOf(provenanceJson);
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (entrySeq < 0) {
            throw new IllegalArgumentException("entrySeq must be non-negative");
        }
        if (basisCutoffItemId <= 0L) {
            throw new IllegalArgumentException("basisCutoffItemId must be positive");
        }
        if (basisMeaningfulEventCount < 0L) {
            throw new IllegalArgumentException("basisMeaningfulEventCount must be non-negative");
        }
    }
}
