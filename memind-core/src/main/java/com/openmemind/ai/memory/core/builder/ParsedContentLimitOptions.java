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

/**
 * Limits applied after the raw bytes have been parsed into structured content.
 */
public record ParsedContentLimitOptions(
        int maxTokens, Integer maxSections, Integer maxPages, Duration maxDuration) {

    public ParsedContentLimitOptions {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (maxSections != null && maxSections <= 0) {
            throw new IllegalArgumentException("maxSections must be positive when provided");
        }
        if (maxPages != null && maxPages <= 0) {
            throw new IllegalArgumentException("maxPages must be positive when provided");
        }
        if (maxDuration != null && (maxDuration.isZero() || maxDuration.isNegative())) {
            throw new IllegalArgumentException("maxDuration must be positive when provided");
        }
    }
}
