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
package com.openmemind.ai.memory.core.plugin;

import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.resource.ContentCapability;
import java.util.Objects;

/**
 * Plugin-owned source ingestion policy for parser-backed rawdata extraction.
 */
public record RawDataIngestionPolicy(
        String contentType, String governanceType, SourceLimitOptions sourceLimit) {

    public RawDataIngestionPolicy {
        contentType = Objects.requireNonNull(contentType, "contentType");
        governanceType = Objects.requireNonNull(governanceType, "governanceType");
        sourceLimit = Objects.requireNonNull(sourceLimit, "sourceLimit");
        if (governanceType.isBlank()) {
            throw new IllegalArgumentException("governanceType must not be blank");
        }
    }

    public boolean supports(ContentCapability capability) {
        return contentType.equals(capability.contentType())
                && governanceType.equals(capability.governanceType());
    }
}
