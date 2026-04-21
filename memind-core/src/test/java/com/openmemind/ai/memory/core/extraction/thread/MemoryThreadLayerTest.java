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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.builder.MemoryThreadDerivationOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadOptions;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MemoryThreadLayerTest {

    @Test
    void extractEnqueuesCommittedItemsAndWakesWorkerWithoutBlockingItemWrites() {
        MemoryItemExtractStep delegate = mock(MemoryItemExtractStep.class);
        ThreadProjectionStore store = mock(ThreadProjectionStore.class);
        ThreadIntakeWorker worker = mock(ThreadIntakeWorker.class);
        MemoryThreadLayer layer = new MemoryThreadLayer(delegate, store, worker, enabledOptions());
        MemoryItem item = item(101L, "Started seeing friends again");
        MemoryItemResult result = new MemoryItemResult(List.of(item), List.of());
        when(delegate.extract(
                        eq(TestMemoryIds.userAgent()),
                        eq(RawDataResult.empty()),
                        eq(ItemExtractionConfig.defaults())))
                .thenReturn(Mono.just(result));

        StepVerifier.create(
                        layer.extract(
                                TestMemoryIds.userAgent(),
                                RawDataResult.empty(),
                                ItemExtractionConfig.defaults()))
                .expectNext(result)
                .verifyComplete();

        verify(store).ensureRuntime(TestMemoryIds.userAgent(), "thread-core-v1");
        verify(store).enqueue(TestMemoryIds.userAgent(), 101L);
        verify(worker).wake(TestMemoryIds.userAgent());
    }

    @Test
    void enqueueFailureMarksRuntimeRebuildRequiredWithoutFailingExtraction() {
        MemoryItemExtractStep delegate = mock(MemoryItemExtractStep.class);
        ThreadProjectionStore store = mock(ThreadProjectionStore.class);
        ThreadIntakeWorker worker = mock(ThreadIntakeWorker.class);
        MemoryThreadLayer layer = new MemoryThreadLayer(delegate, store, worker, enabledOptions());
        MemoryItem item = item(201L, "Alice followed up again");
        MemoryItemResult result = new MemoryItemResult(List.of(item), List.of());
        when(delegate.extract(
                        eq(TestMemoryIds.userAgent()),
                        eq(RawDataResult.empty()),
                        eq(ItemExtractionConfig.defaults())))
                .thenReturn(Mono.just(result));
        doThrow(new IllegalStateException("boom"))
                .when(store)
                .enqueue(TestMemoryIds.userAgent(), 201L);

        StepVerifier.create(
                        layer.extract(
                                TestMemoryIds.userAgent(),
                                RawDataResult.empty(),
                                ItemExtractionConfig.defaults()))
                .expectNext(result)
                .verifyComplete();

        verify(store).markRebuildRequired(TestMemoryIds.userAgent(), "enqueue failure");
        verify(worker, never()).wake(TestMemoryIds.userAgent());
    }

    private static MemoryItem item(Long id, String content) {
        return new MemoryItem(
                id,
                TestMemoryIds.userAgent().toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
                "conversation",
                "vec-" + id,
                "raw-" + id,
                "hash-" + id,
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                Map.of(),
                Instant.parse("2026-04-18T00:00:00Z"),
                MemoryItemType.FACT);
    }

    private static MemoryThreadOptions enabledOptions() {
        return MemoryThreadOptions.defaults()
                .withEnabled(true)
                .withDerivation(MemoryThreadDerivationOptions.defaults().withEnabled(true));
    }
}
