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
package com.openmemind.ai.memory.server.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.server.support.TestMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MemoryRuntimeManagerTest {

    @Test
    void oldRuntimeClosesOnlyAfterLastLeaseIsReleased() {
        AtomicInteger closeCount = new AtomicInteger();
        Memory oldMemory = closeTrackingMemory(closeCount);
        MemoryRuntimeManager manager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(oldMemory, MemoryBuildOptions.defaults(), 1));

        RuntimeLease lease = manager.acquire();

        manager.swap(new TestMemory(), MemoryBuildOptions.defaults(), 2);

        assertThat(closeCount).hasValue(0);
        assertThat(manager.currentVersion()).isEqualTo(2);

        lease.close();

        assertThat(closeCount).hasValue(1);
    }

    @Test
    void acquireAfterSwapReturnsNewRuntime() {
        Memory oldMemory = new TestMemory();
        Memory newMemory = new TestMemory();
        MemoryRuntimeManager manager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(oldMemory, MemoryBuildOptions.defaults(), 1));

        manager.swap(newMemory, MemoryBuildOptions.defaults(), 2);

        try (RuntimeLease lease = manager.acquire()) {
            assertThat(lease.handle().memory()).isSameAs(newMemory);
            assertThat(lease.handle().version()).isEqualTo(2);
        }
    }

    @Test
    void swapCanInstallFirstRuntimeWhenManagerStartsEmpty() {
        Memory firstMemory = new TestMemory();
        MemoryRuntimeManager manager = new MemoryRuntimeManager();

        manager.swap(firstMemory, MemoryBuildOptions.defaults(), 1);

        try (RuntimeLease lease = manager.acquire()) {
            assertThat(lease.handle().memory()).isSameAs(firstMemory);
            assertThat(lease.handle().version()).isEqualTo(1);
        }
    }

    @Test
    void concurrentAcquireDuringSwapClosesOldRuntimeExactlyOnce() throws Exception {
        AtomicInteger closeCount = new AtomicInteger();
        Memory oldMemory = closeTrackingMemory(closeCount);
        MemoryRuntimeManager manager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(oldMemory, MemoryBuildOptions.defaults(), 1));

        int threads = 4;
        int rounds = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < rounds; j++) {
                        try (RuntimeLease ignored = manager.acquire()) {
                            // hold and release a lease while the runtime is being swapped
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (int version = 2; version <= 20; version++) {
                manager.swap(new TestMemory(), MemoryBuildOptions.defaults(), version);
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }
        finally {
            executor.shutdownNow();
        }

        assertThat(closeCount).hasValue(1);
    }

    private static Memory closeTrackingMemory(AtomicInteger closeCount) {
        return new TestMemory(closeCount::incrementAndGet);
    }
}
