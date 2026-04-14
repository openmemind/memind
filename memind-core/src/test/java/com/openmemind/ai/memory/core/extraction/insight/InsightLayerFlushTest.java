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
package com.openmemind.ai.memory.core.extraction.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class InsightLayerFlushTest {

    @Test
    void flushSkipsWhenNoInsightTypesConfigured() {
        MemoryStore memoryStore = mock(MemoryStore.class);
        InsightOperations insightOperations = mock(InsightOperations.class);
        InsightBuildScheduler scheduler = mock(InsightBuildScheduler.class);
        MemoryId memoryId = com.openmemind.ai.memory.core.data.DefaultMemoryId.of("user", "agent");

        when(memoryStore.insightOperations()).thenReturn(insightOperations);
        when(insightOperations.listInsightTypes()).thenReturn(List.of());

        InsightLayer layer = new InsightLayer(memoryStore, scheduler);

        layer.flush(memoryId);

        verify(scheduler).awaitPending(memoryId, 5, java.util.concurrent.TimeUnit.MINUTES);
        verify(scheduler, never()).flushSync(eq(memoryId), any(String.class), eq(null));
        verify(scheduler, never()).forceResummarizeBranchIfEmpty(eq(memoryId), any(), eq(null));
        verify(scheduler).drainRootTasks(memoryId, 5, java.util.concurrent.TimeUnit.MINUTES);
    }

    @Test
    void flushesInsightTypesSequentiallyBeforeRootDrain() {
        MemoryStore memoryStore = mock(MemoryStore.class);
        InsightOperations insightOperations = mock(InsightOperations.class);
        InsightBuildScheduler scheduler = mock(InsightBuildScheduler.class);
        MemoryId memoryId = com.openmemind.ai.memory.core.data.DefaultMemoryId.of("user", "agent");

        when(memoryStore.insightOperations()).thenReturn(insightOperations);
        when(insightOperations.listInsightTypes())
                .thenReturn(
                        List.of(
                                DefaultInsightTypes.identity(),
                                DefaultInsightTypes.preferences(),
                                DefaultInsightTypes.experiences(),
                                DefaultInsightTypes.profile()));

        AtomicInteger activeFlushes = new AtomicInteger();
        AtomicInteger maxConcurrentFlushes = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            int current = activeFlushes.incrementAndGet();
                            maxConcurrentFlushes.accumulateAndGet(current, Math::max);
                            try {
                                Thread.sleep(40L);
                            } finally {
                                activeFlushes.decrementAndGet();
                            }
                            return null;
                        })
                .when(scheduler)
                .flushSync(eq(memoryId), any(String.class), eq(null));

        InsightLayer layer = new InsightLayer(memoryStore, scheduler);

        layer.flush(memoryId);

        assertEquals(1, maxConcurrentFlushes.get());

        InOrder inOrder = inOrder(scheduler);
        inOrder.verify(scheduler).awaitPending(memoryId, 5, java.util.concurrent.TimeUnit.MINUTES);
        inOrder.verify(scheduler).flushSync(memoryId, "identity", null);
        inOrder.verify(scheduler).flushSync(memoryId, "preferences", null);
        inOrder.verify(scheduler).flushSync(memoryId, "experiences", null);
        inOrder.verify(scheduler)
                .forceResummarizeBranchIfEmpty(memoryId, DefaultInsightTypes.identity(), null);
        inOrder.verify(scheduler)
                .forceResummarizeBranchIfEmpty(memoryId, DefaultInsightTypes.preferences(), null);
        inOrder.verify(scheduler)
                .forceResummarizeBranchIfEmpty(memoryId, DefaultInsightTypes.experiences(), null);
        inOrder.verify(scheduler)
                .drainRootTasks(memoryId, 5, java.util.concurrent.TimeUnit.MINUTES);
    }
}
