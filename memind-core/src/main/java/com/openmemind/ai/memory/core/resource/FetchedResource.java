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

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable descriptor returned by a {@link ResourceFetcher}.
 */
public record FetchedResource(String sourceUrl, String fileName, byte[] data, String mimeType) {

    public FetchedResource {
        Objects.requireNonNull(sourceUrl, "sourceUrl is required");
        Objects.requireNonNull(fileName, "fileName is required");
        Objects.requireNonNull(data, "data is required");
        Objects.requireNonNull(mimeType, "mimeType is required");
        data = Arrays.copyOf(data, data.length);
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }
}
