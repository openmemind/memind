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
package com.openmemind.ai.memory.core.prompt;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory implementation of {@link PromptRegistry}. */
public final class InMemoryPromptRegistry implements PromptRegistry {

    private final ConcurrentHashMap<PromptType, String> overrides;

    private InMemoryPromptRegistry(Map<PromptType, String> initialOverrides) {
        this.overrides = new ConcurrentHashMap<>(initialOverrides);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void override(PromptType type, String instruction) {
        overrides.put(
                Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(instruction, "instruction"));
    }

    public void removeOverride(PromptType type) {
        overrides.remove(Objects.requireNonNull(type, "type"));
    }

    @Override
    public String getOverride(PromptType type) {
        return overrides.get(type);
    }

    public static final class Builder {
        private final Map<PromptType, String> initialOverrides = new EnumMap<>(PromptType.class);

        public Builder override(PromptType type, String instruction) {
            initialOverrides.put(
                    Objects.requireNonNull(type, "type"),
                    Objects.requireNonNull(instruction, "instruction"));
            return this;
        }

        public InMemoryPromptRegistry build() {
            return new InMemoryPromptRegistry(initialOverrides);
        }
    }
}
