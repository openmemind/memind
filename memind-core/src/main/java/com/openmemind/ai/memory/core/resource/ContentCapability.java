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

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.BuiltinContentProfiles;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Structured runtime capability exposed by a registered parser.
 */
public record ContentCapability(
        String parserId,
        String contentType,
        String contentProfile,
        ContentGovernanceType governanceType,
        Set<String> supportedMimeTypes,
        Set<String> supportedExtensions,
        int priority) {

    public ContentCapability {
        parserId = Objects.requireNonNull(parserId, "parserId is required");
        contentType = Objects.requireNonNull(contentType, "contentType is required");
        contentProfile = Objects.requireNonNull(contentProfile, "contentProfile is required");
        governanceType = Objects.requireNonNull(governanceType, "governanceType is required");
        supportedMimeTypes = immutableCopy(supportedMimeTypes, "supportedMimeTypes");
        supportedExtensions = immutableCopy(supportedExtensions, "supportedExtensions");
    }

    public ContentCapability(
            String parserId,
            String contentType,
            String contentProfile,
            Set<String> supportedMimeTypes,
            Set<String> supportedExtensions,
            int priority) {
        this(
                parserId,
                contentType,
                contentProfile,
                BuiltinContentProfiles.governanceTypeOf(contentProfile)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Non-builtin contentProfile requires explicit"
                                                        + " governanceType: "
                                                        + contentProfile)),
                supportedMimeTypes,
                supportedExtensions,
                priority);
    }

    private static Set<String> immutableCopy(Set<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " is required");
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
