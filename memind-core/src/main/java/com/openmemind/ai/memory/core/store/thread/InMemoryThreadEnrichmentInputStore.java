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
package com.openmemind.ai.memory.core.store.thread;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEnrichmentInput;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.SerializationFeature;

/**
 * In-memory authoritative enrichment input store.
 */
public class InMemoryThreadEnrichmentInputStore implements ThreadEnrichmentInputStore {

    private static final Comparator<MemoryThreadEnrichmentInput> REPLAY_ORDER =
            Comparator.comparingLong(MemoryThreadEnrichmentInput::basisCutoffItemId)
                    .thenComparingLong(MemoryThreadEnrichmentInput::basisMeaningfulEventCount)
                    .thenComparing(MemoryThreadEnrichmentInput::inputRunKey)
                    .thenComparingInt(MemoryThreadEnrichmentInput::entrySeq);

    private final Map<String, Map<String, MemoryThreadEnrichmentInput>> inputsByMemoryId =
            new ConcurrentHashMap<>();
    private final ThreadProjectionStore projectionStore;

    public InMemoryThreadEnrichmentInputStore(ThreadProjectionStore projectionStore) {
        this.projectionStore = Objects.requireNonNull(projectionStore, "projectionStore");
    }

    @Override
    public synchronized ThreadEnrichmentAppendResult appendRunAndEnqueueReplay(
            MemoryId memoryId,
            long replayCutoffItemId,
            List<MemoryThreadEnrichmentInput> runInputs) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (replayCutoffItemId <= 0L) {
            throw new IllegalArgumentException("replayCutoffItemId must be positive");
        }
        if (runInputs == null || runInputs.isEmpty()) {
            throw new IllegalArgumentException("runInputs must not be empty");
        }

        String memoryIdentifier = memoryId.toIdentifier();
        Map<String, MemoryThreadEnrichmentInput> inputs =
                inputsByMemoryId.computeIfAbsent(
                        memoryIdentifier, ignored -> new LinkedHashMap<>());
        List<MemoryThreadEnrichmentInput> toInsert = new ArrayList<>();
        for (MemoryThreadEnrichmentInput input : runInputs) {
            validateInput(memoryIdentifier, input);
            MemoryThreadEnrichmentInput existing = inputs.get(key(input));
            if (existing == null) {
                toInsert.add(input);
                continue;
            }
            if (!equivalent(existing, input)) {
                throw new IllegalStateException("conflicting duplicate enrichment input");
            }
        }
        if (toInsert.isEmpty()) {
            return ThreadEnrichmentAppendResult.DUPLICATE_EQUIVALENT;
        }

        List<String> insertedKeys = new ArrayList<>(toInsert.size());
        try {
            for (MemoryThreadEnrichmentInput input : toInsert) {
                String key = key(input);
                inputs.put(key, input);
                insertedKeys.add(key);
            }
            projectionStore.enqueueReplay(memoryId, replayCutoffItemId);
            return ThreadEnrichmentAppendResult.INSERTED;
        } catch (RuntimeException error) {
            insertedKeys.forEach(inputs::remove);
            throw error;
        }
    }

    @Override
    public synchronized List<MemoryThreadEnrichmentInput> listReplayable(
            MemoryId memoryId, long cutoffItemId, String materializationPolicyVersion) {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(materializationPolicyVersion, "materializationPolicyVersion");
        if (cutoffItemId <= 0L) {
            return List.of();
        }
        return inputsByMemoryId.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .filter(input -> input.basisCutoffItemId() <= cutoffItemId)
                .filter(
                        input ->
                                Objects.equals(
                                        input.basisMaterializationPolicyVersion(),
                                        materializationPolicyVersion))
                .sorted(REPLAY_ORDER)
                .toList();
    }

    private static void validateInput(String memoryIdentifier, MemoryThreadEnrichmentInput input) {
        Objects.requireNonNull(input, "input");
        if (!Objects.equals(memoryIdentifier, input.memoryId())) {
            throw new IllegalArgumentException("runInputs memoryId must match append memoryId");
        }
    }

    private static String key(MemoryThreadEnrichmentInput input) {
        return input.inputRunKey() + "#" + input.entrySeq();
    }

    private static boolean equivalent(
            MemoryThreadEnrichmentInput left, MemoryThreadEnrichmentInput right) {
        return Objects.equals(left.memoryId(), right.memoryId())
                && Objects.equals(left.threadKey(), right.threadKey())
                && Objects.equals(left.inputRunKey(), right.inputRunKey())
                && left.entrySeq() == right.entrySeq()
                && left.basisCutoffItemId() == right.basisCutoffItemId()
                && left.basisMeaningfulEventCount() == right.basisMeaningfulEventCount()
                && Objects.equals(
                        left.basisMaterializationPolicyVersion(),
                        right.basisMaterializationPolicyVersion())
                && Objects.equals(
                        canonicalJson(left.payloadJson()), canonicalJson(right.payloadJson()))
                && Objects.equals(
                        canonicalJson(left.provenanceJson()),
                        canonicalJson(right.provenanceJson()));
    }

    private static String canonicalJson(Map<String, Object> value) {
        try {
            return JsonUtils.newMapper()
                    .writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(value == null ? Map.of() : value);
        } catch (tools.jackson.core.JacksonException error) {
            throw new IllegalStateException("Failed to canonicalize enrichment input JSON", error);
        }
    }
}
