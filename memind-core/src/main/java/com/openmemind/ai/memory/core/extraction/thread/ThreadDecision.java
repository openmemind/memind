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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import java.util.Objects;

/**
 * Deterministic attach/create/ignore decision for one intake signal.
 */
public record ThreadDecision(
        Action action,
        String threadKey,
        MemoryThreadType threadType,
        String anchorKind,
        String anchorKey,
        long triggerItemId,
        String reason) {

    public ThreadDecision {
        action = Objects.requireNonNull(action, "action");
        if (triggerItemId <= 0) {
            throw new IllegalArgumentException("triggerItemId must be positive");
        }
        if (action != Action.IGNORE) {
            Objects.requireNonNull(threadKey, "threadKey");
            threadType = Objects.requireNonNull(threadType, "threadType");
            Objects.requireNonNull(anchorKind, "anchorKind");
            Objects.requireNonNull(anchorKey, "anchorKey");
        }
    }

    public static ThreadDecision attach(
            String threadKey,
            MemoryThreadType threadType,
            String anchorKind,
            String anchorKey,
            long triggerItemId) {
        return new ThreadDecision(
                Action.ATTACH, threadKey, threadType, anchorKind, anchorKey, triggerItemId, null);
    }

    public static ThreadDecision create(
            String threadKey,
            MemoryThreadType threadType,
            String anchorKind,
            String anchorKey,
            long triggerItemId) {
        return new ThreadDecision(
                Action.CREATE, threadKey, threadType, anchorKind, anchorKey, triggerItemId, null);
    }

    public static ThreadDecision ignore(long triggerItemId, String reason) {
        return new ThreadDecision(Action.IGNORE, null, null, null, null, triggerItemId, reason);
    }

    public enum Action {
        ATTACH,
        CREATE,
        IGNORE
    }
}
