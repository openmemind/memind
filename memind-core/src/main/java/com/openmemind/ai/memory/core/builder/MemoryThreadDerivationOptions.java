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

/**
 * Derivation execution switches for memory-thread building.
 */
public record MemoryThreadDerivationOptions(boolean enabled, boolean async) {

    public static MemoryThreadDerivationOptions defaults() {
        return new MemoryThreadDerivationOptions(false, true);
    }

    public MemoryThreadDerivationOptions withEnabled(boolean enabled) {
        return new MemoryThreadDerivationOptions(enabled, async);
    }

    public MemoryThreadDerivationOptions withAsync(boolean async) {
        return new MemoryThreadDerivationOptions(enabled, async);
    }
}
