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

import java.util.Objects;

/**
 * Core-owned runtime defaults used during memory bootstrap.
 */
public final class MemoryBuildOptions {

    private final ExtractionOptions extraction;
    private final RetrievalOptions retrieval;

    private MemoryBuildOptions(Builder builder) {
        this.extraction = Objects.requireNonNull(builder.extraction, "extraction");
        this.retrieval = Objects.requireNonNull(builder.retrieval, "retrieval");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MemoryBuildOptions defaults() {
        return builder().build();
    }

    public ExtractionOptions extraction() {
        return extraction;
    }

    public RetrievalOptions retrieval() {
        return retrieval;
    }

    public static final class Builder {

        private ExtractionOptions extraction = ExtractionOptions.defaults();
        private RetrievalOptions retrieval = RetrievalOptions.defaults();

        private Builder() {}

        public Builder extraction(ExtractionOptions extraction) {
            this.extraction = Objects.requireNonNull(extraction, "extraction");
            return this;
        }

        public Builder retrieval(RetrievalOptions retrieval) {
            this.retrieval = Objects.requireNonNull(retrieval, "retrieval");
            return this;
        }

        public MemoryBuildOptions build() {
            return new MemoryBuildOptions(this);
        }
    }
}
