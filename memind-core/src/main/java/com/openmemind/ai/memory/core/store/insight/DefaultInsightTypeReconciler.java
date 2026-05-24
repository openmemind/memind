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

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import java.util.List;

/**
 * Reconciles built-in insight type definitions into a store without overwriting local
 * customizations.
 */
public final class DefaultInsightTypeReconciler {

    private DefaultInsightTypeReconciler() {}

    public static void reconcile(InsightOperations operations) {
        if (operations == null) {
            return;
        }
        List<MemoryInsightType> missing =
                DefaultInsightTypes.all().stream()
                        .filter(type -> operations.getInsightType(type.name()).isEmpty())
                        .toList();
        if (!missing.isEmpty()) {
            operations.upsertInsightTypes(missing);
        }
    }
}
