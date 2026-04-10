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
package com.openmemind.ai.memory.core.extraction;

import com.openmemind.ai.memory.core.resource.ResourceUrlValidator;
import java.util.Objects;

/**
 * Remote URL payload for downloader-backed extraction requests.
 */
public record RawUrlInput(String sourceUrl, String fileName, String mimeType) {

    public RawUrlInput {
        Objects.requireNonNull(sourceUrl, "sourceUrl is required");
        sourceUrl = sourceUrl.trim();
        if (sourceUrl.isEmpty()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        ResourceUrlValidator.requireSupportedSourceUrl(sourceUrl);
        fileName = normalizeOptional(fileName);
        mimeType = normalizeOptional(mimeType);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
