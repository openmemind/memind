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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    void concurrentAcquireAndSwapNeverHandsOutDrainingRuntimeAndClosesOnce() throws Exception {
        AtomicInteger initialCloseCount = new AtomicInteger();
        Memory initialMemory = closeTrackingMemory(initialCloseCount);
        RuntimeHandle initialHandle =
                new RuntimeHandle(initialMemory, MemoryBuildOptions.defaults(), 1);
        MemoryRuntimeManager manager = new MemoryRuntimeManager(initialHandle);

        List<AtomicInteger> swapCloseCounts = new ArrayList<>();
        List<RuntimeHandle> allHandles = new ArrayList<>();
        allHandles.add(initialHandle);

        int readerThreads = 6;
        int iterationsPerReader = 250;
        int swapCount = 40;

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(readerThreads + 1);

        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int t = 0; t < readerThreads; t++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    startLatch.await();
                                    for (int i = 0; i < iterationsPerReader; i++) {
                                        try (RuntimeLease lease = manager.acquire()) {
                                            assertThat(lease.handle().draining().get()).isFalse();
                                            assertThat(lease.handle().inFlightRequests().get())
                                                    .isGreaterThanOrEqualTo(1);
                                        }
                                    }
                                    return null;
                                }));
            }

            Callable<Void> swapTask =
                    () -> {
                        startLatch.await();
                        for (int v = 2; v <= swapCount + 1; v++) {
                            AtomicInteger closeCounter = new AtomicInteger();
                            Memory nextMemory = closeTrackingMemory(closeCounter);
                            synchronized (swapCloseCounts) {
                                swapCloseCounts.add(closeCounter);
                            }
                            synchronized (allHandles) {
                                allHandles.add(manager.currentHandle());
                            }
                            manager.swap(nextMemory, MemoryBuildOptions.defaults(), v);
                        }
                        return null;
                    };
            futures.add(executor.submit(swapTask));

            startLatch.countDown();
            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Swap once more so the final active runtime also becomes drained and closes.
        manager.swap(new TestMemory(), MemoryBuildOptions.defaults(), swapCount + 2);

        assertThat(initialCloseCount).hasValue(1);
        synchronized (swapCloseCounts) {
            for (AtomicInteger counter : swapCloseCounts) {
                assertThat(counter).hasValue(1);
            }
        }
        synchronized (allHandles) {
            for (RuntimeHandle handle : allHandles) {
                assertThat(handle.inFlightRequests()).hasValue(0);
            }
        }
    }

    @Test
    void closeRunsAtMostOnceUnderConcurrentLeaseReleases() throws Exception {
        AtomicInteger closeCount = new AtomicInteger();
        Memory oldMemory = closeTrackingMemory(closeCount);
        RuntimeHandle oldHandle = new RuntimeHandle(oldMemory, MemoryBuildOptions.defaults(), 1);
        MemoryRuntimeManager manager = new MemoryRuntimeManager(oldHandle);

        int leaseCount = 8;
        List<RuntimeLease> leases = new ArrayList<>();
        for (int i = 0; i < leaseCount; i++) {
            leases.add(manager.acquire());
        }

        manager.swap(new TestMemory(), MemoryBuildOptions.defaults(), 2);
        assertThat(closeCount).hasValue(0);

        CountDownLatch startRelease = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(leaseCount);
        try {
            List<Future<?>> releaseFutures = new ArrayList<>();
            for (RuntimeLease lease : leases) {
                releaseFutures.add(
                        executor.submit(
                                () -> {
                                    startRelease.await();
                                    lease.close();
                                    return null;
                                }));
            }
            startRelease.countDown();
            for (Future<?> f : releaseFutures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(closeCount).hasValue(1);
        assertThat(oldHandle.inFlightRequests()).hasValue(0);
    }

    @Test
    void acquireOnNewRuntimeIsNotBlockedBySlowCloseOfOldRuntime() throws Exception {
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch allowCloseToFinish = new CountDownLatch(1);
        AtomicInteger closeCount = new AtomicInteger();

        Memory slowOldMemory =
                new TestMemory(
                        () -> {
                            closeCount.incrementAndGet();
                            closeStarted.countDown();
                            try {
                                allowCloseToFinish.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });

        MemoryRuntimeManager manager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(slowOldMemory, MemoryBuildOptions.defaults(), 1));

        AtomicBoolean swapFinished = new AtomicBoolean(false);
        Thread swapThread =
                new Thread(
                        () -> {
                            manager.swap(new TestMemory(), MemoryBuildOptions.defaults(), 2);
                            swapFinished.set(true);
                        });
        swapThread.start();

        assertThat(closeStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(swapFinished).isFalse();

        // Acquiring the new runtime should not wait for slowOldMemory.close() to finish.
        try (RuntimeLease newLease = manager.acquire()) {
            assertThat(newLease.handle().version()).isEqualTo(2);
        }

        allowCloseToFinish.countDown();
        swapThread.join(5_000);
        assertThat(swapFinished).isTrue();
        assertThat(closeCount).hasValue(1);
    }

    private static Memory closeTrackingMemory(AtomicInteger closeCount) {
        return new TestMemory(closeCount::incrementAndGet);
    }
}
