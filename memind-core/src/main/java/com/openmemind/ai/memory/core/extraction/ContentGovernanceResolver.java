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

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves authoritative governance families from normalized multimodal metadata.
 */
public final class ContentGovernanceResolver {

    private ContentGovernanceResolver() {}

    public static ContentGovernanceType resolveRequired(Map<String, Object> metadata) {
        Objects.requireNonNull(metadata, "metadata");
        Object governanceType = metadata.get("governanceType");
        if (governanceType != null) {
            return ContentGovernanceType.valueOf(governanceType.toString());
        }
        Object contentProfile = metadata.get("contentProfile");
        return BuiltinContentProfiles.governanceTypeOf(
                        contentProfile == null ? null : contentProfile.toString())
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Missing governanceType for contentProfile="
                                                + contentProfile));
    }
}
