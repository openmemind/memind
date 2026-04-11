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

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.Objects;

/**
 * Input to a downloader before opening the remote resource.
 */
public record ResourceFetchRequest(
        MemoryId memoryId, String sourceUrl, String requestedFileName, String requestedMimeType) {

    public ResourceFetchRequest {
        Objects.requireNonNull(memoryId, "memoryId is required");
        Objects.requireNonNull(sourceUrl, "sourceUrl is required");
        ResourceUrlValidator.requireSupportedSourceUrl(sourceUrl);
        requestedFileName = normalize(requestedFileName);
        requestedMimeType = normalize(requestedMimeType);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
