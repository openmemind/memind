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
package com.openmemind.ai.memory.core.store.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultInsightTypeReconcilerTest {

    @Test
    void insertsMissingBuiltInTypesOnly() {
        var ops = new InMemoryInsightOperations();
        ops.upsertInsightTypes(List.of(DefaultInsightTypes.identity()));

        DefaultInsightTypeReconciler.reconcile(ops);

        assertThat(ops.getInsightType("identity")).isPresent();
        assertThat(ops.getInsightType("tools")).isPresent();
    }

    @Test
    void preservesExistingCustomizedType() {
        var ops = new InMemoryInsightOperations();
        var customized = DefaultInsightTypes.tools().withTargetTokens(1234);
        ops.upsertInsightTypes(List.of(customized));

        DefaultInsightTypeReconciler.reconcile(ops);

        assertThat(ops.getInsightType("tools").orElseThrow().targetTokens()).isEqualTo(1234);
    }
}
