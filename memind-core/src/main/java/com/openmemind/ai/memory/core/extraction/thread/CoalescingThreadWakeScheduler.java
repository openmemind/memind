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

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CoalescingThreadWakeScheduler implements ThreadWakeScheduler {

    private static final Logger log = LoggerFactory.getLogger(CoalescingThreadWakeScheduler.class);
    private static final int MAX_WORKER_THREADS =
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));

    private final ThreadIntakeWorker worker;
    private final Executor executor;
    private final Runnable closeAction;
    private final ThreadDerivationMetrics metrics;
    private final ConcurrentMap<String, Slot> slots = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public CoalescingThreadWakeScheduler(ThreadIntakeWorker worker) {
        this(worker, createExecutor(), null, ThreadDerivationMetrics.NOOP);
    }

    public CoalescingThreadWakeScheduler(
            ThreadIntakeWorker worker, ThreadDerivationMetrics metrics) {
        this(worker, createExecutor(), null, metrics);
    }

    CoalescingThreadWakeScheduler(
            ThreadIntakeWorker worker, Executor executor, Runnable closeAction) {
        this(worker, executor, closeAction, ThreadDerivationMetrics.NOOP);
    }

    CoalescingThreadWakeScheduler(
            ThreadIntakeWorker worker,
            Executor executor,
            Runnable closeAction,
            ThreadDerivationMetrics metrics) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.closeAction = closeAction != null ? closeAction : defaultCloseAction(executor);
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public void schedule(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        Slot slot = slot(memoryId);
        synchronized (slot.monitor) {
            if (closed.get() || slot.closed) {
                return;
            }
            if (slot.running) {
                slot.rerunRequested = true;
                return;
            }
            slot.running = true;
            slot.rerunRequested = false;
        }
        submitAsync(memoryId, slot);
    }

    @Override
    public void flush(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        Slot slot = slot(memoryId);
        while (true) {
            synchronized (slot.monitor) {
                if (closed.get() || slot.closed) {
                    return;
                }
                if (!slot.running) {
                    slot.running = true;
                    slot.rerunRequested = false;
                    break;
                }
                slot.rerunRequested = true;
                try {
                    slot.monitor.wait();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        drain(memoryId, slot, true);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        slots.values().forEach(Slot::close);
        closeAction.run();
    }

    private void submitAsync(MemoryId memoryId, Slot slot) {
        try {
            executor.execute(() -> drain(memoryId, slot, false));
        } catch (RuntimeException error) {
            synchronized (slot.monitor) {
                slot.running = false;
                slot.rerunRequested = false;
                slot.monitor.notifyAll();
            }
            metrics.onWakeSubmissionFailed();
            log.warn("Failed to submit thread wake for memoryId={}", memoryId, error);
        }
    }

    private void drain(MemoryId memoryId, Slot slot, boolean inlineFlush) {
        synchronized (slot.monitor) {
            if (closed.get() || slot.closed) {
                slot.running = false;
                slot.rerunRequested = false;
                slot.monitor.notifyAll();
                return;
            }
            // Duplicate schedules while a wake is only queued are absorbed into the initial drain.
            slot.rerunRequested = false;
        }

        while (true) {
            try {
                worker.wake(memoryId);
            } catch (RuntimeException error) {
                log.warn(
                        "Thread wake failed for memoryId={} inlineFlush={}",
                        memoryId,
                        inlineFlush,
                        error);
            }
            synchronized (slot.monitor) {
                if (closed.get() || slot.closed) {
                    slot.running = false;
                    slot.rerunRequested = false;
                    slot.monitor.notifyAll();
                    return;
                }
                if (!slot.rerunRequested) {
                    slot.running = false;
                    slot.monitor.notifyAll();
                    return;
                }
                slot.rerunRequested = false;
            }
        }
    }

    private Slot slot(MemoryId memoryId) {
        return slots.computeIfAbsent(memoryId.toIdentifier(), ignored -> new Slot());
    }

    private static ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(MAX_WORKER_THREADS, new WakeThreadFactory());
    }

    private static Runnable defaultCloseAction(Executor executor) {
        if (executor instanceof ExecutorService executorService) {
            return () -> {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException error) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            };
        }
        return () -> {};
    }

    private static final class Slot {

        private final Object monitor = new Object();
        private boolean running;
        private boolean rerunRequested;
        private boolean closed;

        private void close() {
            synchronized (monitor) {
                closed = true;
                monitor.notifyAll();
            }
        }
    }

    private static final class WakeThreadFactory implements ThreadFactory {

        private int sequence = 1;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "memory-thread-wake-" + sequence++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
