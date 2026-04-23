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
package com.openmemind.ai.memory.core.extraction.thread;

import com.openmemind.ai.memory.core.builder.MemoryThreadOptions;
import com.openmemind.ai.memory.core.utils.HashUtils;
import java.util.Objects;

/**
 * Builds the effective thread materialization policy from public options.
 */
public final class ThreadMaterializationPolicyFactory {

    private static final String ENGINE_FAMILY = "thread-core-v2";
    private static final String TOKEN_NORMALIZATION_VERSION = "thread-token-v1";
    private static final String SCORING_CONTRACT_VERSION = "thread-scoring-v1";
    private static final String EVENT_TIME_CONTRACT_VERSION = "thread-event-time-v1";
    private static final String METADATA_ALLOWLIST_VERSION = "thread-metadata-refs-v1";
    private static final String RELATIONSHIP_GROUP_VERSION = "thread-relationship-group-v1";

    private ThreadMaterializationPolicyFactory() {}

    public static ThreadMaterializationPolicy from(MemoryThreadOptions options) {
        Objects.requireNonNull(options, "options");
        var rule = options.rule();
        var lifecycle = options.lifecycle();
        String fingerprint =
                String.join(
                        "\n",
                        ENGINE_FAMILY,
                        TOKEN_NORMALIZATION_VERSION,
                        SCORING_CONTRACT_VERSION,
                        EVENT_TIME_CONTRACT_VERSION,
                        METADATA_ALLOWLIST_VERSION,
                        RELATIONSHIP_GROUP_VERSION,
                        "matchThreshold=" + rule.matchThreshold(),
                        "newThreadThreshold=" + rule.newThreadThreshold(),
                        "maxCandidateThreads=" + rule.maxCandidateThreads(),
                        "dormantAfter=" + lifecycle.dormantAfter(),
                        "closeAfter=" + lifecycle.closeAfter());
        return new ThreadMaterializationPolicy(
                ENGINE_FAMILY + ":" + HashUtils.sha256(fingerprint).substring(0, 16),
                rule.matchThreshold(),
                rule.newThreadThreshold(),
                rule.maxCandidateThreads(),
                lifecycle.dormantAfter(),
                lifecycle.closeAfter());
    }
}
