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
package com.openmemind.ai.memory.core.builder;

import java.time.Duration;
import java.util.Objects;

/**
 * Lifecycle thresholds for memory-thread state transitions.
 */
public record MemoryThreadLifecycleOptions(Duration dormantAfter, Duration closeAfter) {

    public MemoryThreadLifecycleOptions {
        Objects.requireNonNull(dormantAfter, "dormantAfter");
        Objects.requireNonNull(closeAfter, "closeAfter");
        if (dormantAfter.isNegative() || dormantAfter.isZero()) {
            throw new IllegalArgumentException("dormantAfter must be positive");
        }
        if (closeAfter.isNegative() || closeAfter.isZero()) {
            throw new IllegalArgumentException("closeAfter must be positive");
        }
        if (closeAfter.compareTo(dormantAfter) < 0) {
            throw new IllegalArgumentException("closeAfter must be >= dormantAfter");
        }
    }

    public static MemoryThreadLifecycleOptions defaults() {
        return new MemoryThreadLifecycleOptions(Duration.ofDays(14), Duration.ofDays(45));
    }

    public MemoryThreadLifecycleOptions withDormantAfter(Duration dormantAfter) {
        return new MemoryThreadLifecycleOptions(dormantAfter, closeAfter);
    }

    public MemoryThreadLifecycleOptions withCloseAfter(Duration closeAfter) {
        return new MemoryThreadLifecycleOptions(dormantAfter, closeAfter);
    }
}
