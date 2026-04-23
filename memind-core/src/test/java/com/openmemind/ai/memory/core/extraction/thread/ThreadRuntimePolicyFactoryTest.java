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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.openmemind.ai.memory.core.builder.MemoryThreadDerivationOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadOptions;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.builder.MemoryThreadLifecycleOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadRuleOptions;
import com.openmemind.ai.memory.core.store.thread.InMemoryThreadProjectionStore;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ThreadRuntimePolicyFactoryTest {

    @Test
    void buildsPolicyFromPublicThreadOptions() {
        MemoryThreadOptions options =
                MemoryThreadOptions.defaults()
                        .withEnabled(true)
                        .withRule(new MemoryThreadRuleOptions(0.81d, 0.73d, 5, 32, 6))
                        .withLifecycle(
                                new MemoryThreadLifecycleOptions(
                                        Duration.ofDays(10), Duration.ofDays(30)));

        ThreadMaterializationPolicy policy = ThreadMaterializationPolicyFactory.from(options);

        assertThat(policy.matchThreshold()).isEqualTo(0.81d);
        assertThat(policy.minimumCreateScoreAfterTwoHit()).isEqualTo(0.73d);
        assertThat(policy.maxCandidateThreads()).isEqualTo(5);
        assertThat(policy.dormantAfter()).isEqualTo(Duration.ofDays(10));
        assertThat(policy.closeAfter()).isEqualTo(Duration.ofDays(30));
        assertThat(policy.version()).startsWith("thread-core-v2:");
    }

    @Test
    void ensureRuntimeMarksProjectionRebuildRequiredWhenPolicyVersionChanges() {
        InMemoryThreadProjectionStore store = new InMemoryThreadProjectionStore();

        store.ensureRuntime(TestMemoryIds.userAgent(), "thread-core-v2:alpha");
        store.ensureRuntime(TestMemoryIds.userAgent(), "thread-core-v2:beta");

        assertThat(store.getRuntime(TestMemoryIds.userAgent()))
                .get()
                .extracting(
                        runtime -> runtime.projectionState(),
                        runtime -> runtime.materializationPolicyVersion(),
                        runtime -> runtime.invalidationReason())
                .containsExactly(
                        MemoryThreadProjectionState.REBUILD_REQUIRED,
                        "thread-core-v2:beta",
                        "policy version changed");
    }

    @Test
    void policyVersionChangeMarksRuntimeRebuildRequiredAcrossAllEntryPoints() {
        ThreadMaterializationPolicy betaPolicy =
                new ThreadMaterializationPolicy(
                        "thread-core-v2:beta",
                        0.81d,
                        0.73d,
                        5,
                        Duration.ofDays(10),
                        Duration.ofDays(30));

        InMemoryMemoryStore layerStore = new InMemoryMemoryStore();
        layerStore.threadOperations().ensureRuntime(TestMemoryIds.userAgent(), "thread-core-v2:alpha");
        MemoryThreadLayer layer =
                new MemoryThreadLayer(
                        mock(MemoryItemExtractStep.class),
                        layerStore.threadOperations(),
                        null,
                        null,
                        MemoryThreadOptions.defaults()
                                .withEnabled(true)
                                .withDerivation(
                                        MemoryThreadDerivationOptions.defaults().withEnabled(true)),
                        betaPolicy);

        assertThat(layer.getThreadRuntimeStatus(TestMemoryIds.userAgent()))
                .extracting(
                        status -> status.projectionState(),
                        status -> status.materializationPolicyVersion(),
                        status -> status.invalidationReason())
                .containsExactly(
                        MemoryThreadProjectionState.REBUILD_REQUIRED,
                        "thread-core-v2:beta",
                        "policy version changed");

        InMemoryMemoryStore workerStore = new InMemoryMemoryStore();
        workerStore.threadOperations().ensureRuntime(TestMemoryIds.userAgent(), "thread-core-v2:alpha");
        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        workerStore.threadOperations(),
                        workerStore.itemOperations(),
                        workerStore.graphOperations(),
                        betaPolicy);

        worker.wake(TestMemoryIds.userAgent());

        assertThat(workerStore.threadOperations().getRuntime(TestMemoryIds.userAgent()))
                .get()
                .extracting(
                        runtime -> runtime.projectionState(),
                        runtime -> runtime.materializationPolicyVersion(),
                        runtime -> runtime.invalidationReason())
                .containsExactly(
                        MemoryThreadProjectionState.REBUILD_REQUIRED,
                        "thread-core-v2:beta",
                        "policy version changed");

        InMemoryMemoryStore rebuildStore = new InMemoryMemoryStore();
        rebuildStore.threadOperations().ensureRuntime(TestMemoryIds.userAgent(), "thread-core-v2:alpha");
        ThreadProjectionRebuilder rebuilder =
                new ThreadProjectionRebuilder(
                        rebuildStore.threadOperations(),
                        rebuildStore.itemOperations(),
                        rebuildStore.graphOperations(),
                        betaPolicy);

        rebuilder.rebuild(TestMemoryIds.userAgent(), 0L);

        assertThat(rebuildStore.threadOperations().getRuntime(TestMemoryIds.userAgent()))
                .get()
                .extracting(
                        runtime -> runtime.projectionState(),
                        runtime -> runtime.materializationPolicyVersion(),
                        runtime -> runtime.invalidationReason())
                .containsExactly(
                        MemoryThreadProjectionState.AVAILABLE,
                        "thread-core-v2:beta",
                        null);
    }
}
