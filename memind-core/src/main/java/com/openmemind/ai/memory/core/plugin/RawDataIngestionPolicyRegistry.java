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

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.resource.ContentCapability;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry for plugin-contributed rawdata ingestion policies.
 */
public final class RawDataIngestionPolicyRegistry {

    private static final RawDataIngestionPolicyRegistry EMPTY =
            new RawDataIngestionPolicyRegistry(List.of());

    private final List<RawDataIngestionPolicy> policies;

    public RawDataIngestionPolicyRegistry(List<RawDataIngestionPolicy> policies) {
        this.policies = List.copyOf(Objects.requireNonNull(policies, "policies"));
        Map<String, RawDataIngestionPolicy> byKey = new LinkedHashMap<>();
        for (RawDataIngestionPolicy policy : this.policies) {
            for (ContentGovernanceType governanceType : policy.governanceTypes()) {
                String key = policy.contentType() + "::" + governanceType.name();
                if (byKey.putIfAbsent(key, policy) != null) {
                    throw new IllegalStateException("Duplicate rawdata ingestion policy: " + key);
                }
            }
        }
    }

    public static RawDataIngestionPolicyRegistry empty() {
        return EMPTY;
    }

    public RawDataIngestionPolicy resolve(ContentCapability capability) {
        return policies.stream()
                .filter(policy -> policy.supports(capability))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No rawdata ingestion policy registered for capability: "
                                                + capability));
    }
}
