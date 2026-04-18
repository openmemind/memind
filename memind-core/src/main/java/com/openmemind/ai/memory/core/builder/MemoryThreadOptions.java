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
 * Top-level build options for memory-thread support.
 */
public record MemoryThreadOptions(
        boolean enabled,
        MemoryThreadDerivationOptions derivation,
        MemoryThreadRuleOptions rule,
        MemoryThreadLifecycleOptions lifecycle) {

    public MemoryThreadOptions {
        derivation = derivation != null ? derivation : MemoryThreadDerivationOptions.defaults();
        rule = rule != null ? rule : MemoryThreadRuleOptions.defaults();
        lifecycle = lifecycle != null ? lifecycle : MemoryThreadLifecycleOptions.defaults();
    }

    public static MemoryThreadOptions defaults() {
        return new MemoryThreadOptions(
                false,
                MemoryThreadDerivationOptions.defaults(),
                MemoryThreadRuleOptions.defaults(),
                MemoryThreadLifecycleOptions.defaults());
    }

    public MemoryThreadOptions withEnabled(boolean enabled) {
        return new MemoryThreadOptions(enabled, derivation, rule, lifecycle);
    }

    public MemoryThreadOptions withDerivation(MemoryThreadDerivationOptions derivation) {
        return new MemoryThreadOptions(enabled, derivation, rule, lifecycle);
    }

    public MemoryThreadOptions withRule(MemoryThreadRuleOptions rule) {
        return new MemoryThreadOptions(enabled, derivation, rule, lifecycle);
    }

    public MemoryThreadOptions withLifecycle(MemoryThreadLifecycleOptions lifecycle) {
        return new MemoryThreadOptions(enabled, derivation, rule, lifecycle);
    }
}
