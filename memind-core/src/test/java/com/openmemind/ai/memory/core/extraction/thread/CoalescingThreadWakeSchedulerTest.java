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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CoalescingThreadWakeSchedulerTest {

    @Test
    void repeatedSchedulesCoalesceToOneAsyncWakePerMemory() {
        ThreadIntakeWorker worker = mock(ThreadIntakeWorker.class);
        QueueingExecutor executor = new QueueingExecutor();
        ThreadWakeScheduler scheduler =
                new CoalescingThreadWakeScheduler(worker, executor, () -> {});
        MemoryId memoryId = TestMemoryIds.userAgent();

        scheduler.schedule(memoryId);
        scheduler.schedule(memoryId);
        executor.runNext();

        verify(worker, times(1)).wake(memoryId);
    }

    @Test
    void flushWaitsForCurrentWakeOwnerThenDrainsInline() throws Exception {
        ThreadIntakeWorker worker = mock(ThreadIntakeWorker.class);
        QueueingExecutor executor = new QueueingExecutor();
        ThreadWakeScheduler scheduler =
                new CoalescingThreadWakeScheduler(worker, executor, () -> {});
        MemoryId memoryId = TestMemoryIds.userAgent();

        scheduler.schedule(memoryId);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread flushThread =
                new Thread(
                        () -> {
                            try {
                                scheduler.flush(memoryId);
                            } catch (Throwable error) {
                                failure.set(error);
                            }
                        });

        flushThread.start();
        executor.runNext();
        flushThread.join(1_000L);

        assertThat(failure.get()).isNull();
        verify(worker, atLeastOnce()).wake(memoryId);
    }

    private static final class QueueingExecutor implements Executor {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runNext() {
            Runnable task = tasks.remove();
            task.run();
        }
    }
}
