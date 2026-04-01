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
package com.openmemind.ai.memory.core.buffer;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Shared JVM-local lock registry for per-session conversation buffer coordination.
 */
public final class ConversationBufferLocks {

    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private ConversationBufferLocks() {}

    public static <T> T withLock(String sessionId, Supplier<T> action) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(action, "action");
        synchronized (LOCKS.computeIfAbsent(sessionId, ignored -> new Object())) {
            return action.get();
        }
    }
}
