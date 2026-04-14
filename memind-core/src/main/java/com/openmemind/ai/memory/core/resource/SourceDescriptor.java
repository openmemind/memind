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
package com.openmemind.ai.memory.core.resource;

import java.util.Objects;

/**
 * Minimal source facts used by parser routing.
 */
public record SourceDescriptor(
        SourceKind sourceKind, String fileName, String mimeType, Long sizeBytes, String sourceUrl) {

    public SourceDescriptor {
        Objects.requireNonNull(sourceKind, "sourceKind is required");
        fileName = normalize(fileName);
        mimeType = normalize(mimeType);
        sourceUrl = normalize(sourceUrl);
        if (sizeBytes != null && sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
