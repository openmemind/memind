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
package com.openmemind.ai.memory.core.vector;

/**
 * Failure raised by ordered vector batch search.
 */
public final class VectorBatchSearchException extends RuntimeException {

    private final int attemptedInvocationCount;

    public VectorBatchSearchException(
            String message, Throwable cause, int attemptedInvocationCount) {
        super(message, cause);
        if (attemptedInvocationCount < 0) {
            throw new IllegalArgumentException("attemptedInvocationCount must be non-negative");
        }
        this.attemptedInvocationCount = attemptedInvocationCount;
    }

    public int attemptedInvocationCount() {
        return attemptedInvocationCount;
    }
}
