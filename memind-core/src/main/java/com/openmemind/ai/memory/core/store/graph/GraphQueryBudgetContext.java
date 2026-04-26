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
package com.openmemind.ai.memory.core.store.graph;

import java.time.Duration;
import java.util.Optional;

/**
 * Thread-local graph query budget used by store implementations to derive statement timeouts.
 */
public final class GraphQueryBudgetContext {

    private static final ThreadLocal<Duration> CURRENT = new ThreadLocal<>();

    private GraphQueryBudgetContext() {}

    public static Scope open(Duration timeout) {
        Duration previous = CURRENT.get();
        CURRENT.set(timeout);
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    public static Optional<Duration> currentTimeout() {
        return Optional.ofNullable(CURRENT.get());
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
