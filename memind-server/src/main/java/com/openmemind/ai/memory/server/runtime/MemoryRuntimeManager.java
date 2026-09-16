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

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public final class MemoryRuntimeManager {

    private final AtomicReference<RuntimeHandle> current;
    private final ReentrantLock lock = new ReentrantLock();

    public MemoryRuntimeManager() {
        this.current = new AtomicReference<>();
    }

    public MemoryRuntimeManager(RuntimeHandle initial) {
        this.current = new AtomicReference<>(initial);
    }

    public RuntimeLease acquire() {
        lock.lock();
        try {
            RuntimeHandle handle = requireCurrentHandle();
            handle.inFlightRequests().incrementAndGet();
            return new RuntimeLease(handle, () -> release(handle));
        }
        finally {
            lock.unlock();
        }
    }

    public void swap(Memory memory, MemoryBuildOptions options, long version) {
        lock.lock();
        try {
            RuntimeHandle next = new RuntimeHandle(memory, options, version);
            RuntimeHandle previous = current.getAndSet(next);
            if (previous == null) {
                return;
            }
            previous.draining().set(true);
            tryClose(previous);
        }
        finally {
            lock.unlock();
        }
    }

    public long currentVersion() {
        return requireCurrentHandle().version();
    }

    public RuntimeHandle currentHandle() {
        return requireCurrentHandle();
    }

    private void release(RuntimeHandle handle) {
        lock.lock();
        try {
            handle.inFlightRequests().decrementAndGet();
            tryClose(handle);
        }
        finally {
            lock.unlock();
        }
    }

    private RuntimeHandle requireCurrentHandle() {
        RuntimeHandle handle = current.get();
        if (handle == null) {
            throw new MemoryRuntimeUnavailableException(
                    "Memory runtime is unavailable. Configure the required runtime beans and"
                            + " restart the application.");
        }
        return handle;
    }

    private void tryClose(RuntimeHandle handle) {
        if (handle.draining().get() && handle.inFlightRequests().get() == 0) {
            try {
                handle.memory().close();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to close drained memory runtime", e);
            }
        }
    }
}
