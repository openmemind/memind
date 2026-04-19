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

import com.openmemind.ai.memory.core.extraction.item.graph.EntityResolutionMode;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.NoOpGraphOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Normalizes requested build options against runtime store capabilities.
 */
public final class MemoryBuildOptionsSanitizer {

    public SanitizationResult sanitize(MemoryBuildOptions options, MemoryStore store) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(store, "store");

        List<String> warnings = new ArrayList<>();
        MemoryBuildOptions effective = options;
        String forcedDisableReason = null;

        if (options.memoryThread().derivation().enabled()
                && !options.extraction().item().graph().enabled()) {
            forcedDisableReason =
                    "memoryThread.derivation.enabled requires extraction.item.graph.enabled;"
                            + " derivation was force-disabled";
            warnings.add(forcedDisableReason);
            effective = disableThreadDerivation(options);
        } else if (options.memoryThread().derivation().enabled()
                && store.graphOperations() == NoOpGraphOperations.INSTANCE) {
            forcedDisableReason =
                    "memoryThread.derivation.enabled requires store-backed typed item graph"
                            + " operations; derivation was force-disabled";
            warnings.add(forcedDisableReason);
            effective = disableThreadDerivation(options);
        }

        if (effective.extraction().item().graph().resolutionMode()
                        == EntityResolutionMode.CONSERVATIVE
                && !store.graphOperationsCapabilities().supportsBoundedEntityKeyLookup()) {
            warnings.add(
                    "extraction.item.graph.resolutionMode=conservative requires bounded"
                            + " entity-key lookup support; resolutionMode was force-disabled to"
                            + " exact");
            effective = disableConservativeResolution(effective);
        }

        if (effective.extraction().item().graph().resolutionMode()
                        == EntityResolutionMode.CONSERVATIVE
                && store.graphOperationsCapabilities().supportsBoundedEntityKeyLookup()
                && !store.graphOperationsCapabilities().supportsHistoricalAliasLookup()) {
            warnings.add(
                    "extraction.item.graph.resolutionMode=conservative is running without"
                            + " historical alias lookup support; Stage 3 historical alias"
                            + " retrieval is disabled and only Stage 2 candidate sources will"
                            + " be used");
        }

        return new SanitizationResult(
                effective, List.copyOf(warnings), Optional.ofNullable(forcedDisableReason));
    }

    private static MemoryBuildOptions disableThreadDerivation(MemoryBuildOptions options) {
        return MemoryBuildOptions.builder()
                .extraction(options.extraction())
                .retrieval(options.retrieval())
                .memoryThread(
                        options.memoryThread()
                                .withDerivation(
                                        options.memoryThread().derivation().withEnabled(false)))
                .build();
    }

    private static MemoryBuildOptions disableConservativeResolution(MemoryBuildOptions options) {
        return MemoryBuildOptions.builder()
                .extraction(
                        new ExtractionOptions(
                                options.extraction().common(),
                                options.extraction().rawdata(),
                                new ItemExtractionOptions(
                                        options.extraction().item().foresightEnabled(),
                                        options.extraction().item().promptBudget(),
                                        options.extraction()
                                                .item()
                                                .graph()
                                                .withResolutionMode(EntityResolutionMode.EXACT)),
                                options.extraction().insight()))
                .retrieval(options.retrieval())
                .memoryThread(options.memoryThread())
                .build();
    }

    public record SanitizationResult(
            MemoryBuildOptions options,
            List<String> warnings,
            Optional<String> memoryThreadForcedDisableReason) {

        public SanitizationResult {
            options = Objects.requireNonNull(options, "options");
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            memoryThreadForcedDisableReason =
                    memoryThreadForcedDisableReason != null
                            ? memoryThreadForcedDisableReason
                            : Optional.empty();
        }
    }
}
