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
package com.openmemind.ai.memory.core.extraction.insight.group.observation;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.observation.MemoryObservation;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Observation contracts for LlmInsightGroupClassifier. */
public final class LlmInsightGroupClassifierObservation {

    private LlmInsightGroupClassifierObservation() {}

    public static Mono<Map<String, List<MemoryItem>>> observe(
            ObservationRegistry observationRegistry,
            MemoryInsightType insightType,
            List<MemoryItem> items,
            Supplier<Mono<Map<String, List<MemoryItem>>>> operation) {
        return observe(observationRegistry, insightType, items, ignored -> operation.get());
    }

    public static Mono<Map<String, List<MemoryItem>>> observe(
            ObservationRegistry observationRegistry,
            MemoryInsightType insightType,
            List<MemoryItem> items,
            Function<GroupClassifyObservationContext, Mono<Map<String, List<MemoryItem>>>>
                    operation) {
        return MemoryObservation.mono(
                observationRegistry,
                GroupClassifyDocument.CLASSIFY,
                GroupClassifyConvention.INSTANCE,
                () -> new GroupClassifyObservationContext(insightType, items),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum GroupClassifyDocument implements ObservationDocumentation {
        CLASSIFY;

        @Override
        public String getName() {
            return "memind.extraction.insight.group.classify";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return GroupClassifyConvention.class;
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeyNames.values();
        }
    }

    public enum HighCardinalityKeyNames implements KeyName {
        INSIGHT_TYPE {
            @Override
            public String asString() {
                return "memind.extraction.insight_type";
            }
        },
        ITEM_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.item_count";
            }
        },
        GROUP_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.insight_group_count";
            }
        };
    }

    public static final class GroupClassifyObservationContext extends Observation.Context {

        private final String insightTypeName;
        private final int itemCount;
        private Integer groupCount;

        public GroupClassifyObservationContext(
                MemoryInsightType insightType, List<MemoryItem> items) {
            this.insightTypeName = insightType == null ? "" : insightType.name();
            this.itemCount = items == null ? 0 : items.size();
        }

        public void recordResult(Map<String, List<MemoryItem>> result) {
            this.groupCount = result.size();
        }
    }

    public static final class GroupClassifyConvention
            implements ObservationConvention<GroupClassifyObservationContext> {

        public static final GroupClassifyConvention INSTANCE = new GroupClassifyConvention();

        @Override
        public String getName() {
            return GroupClassifyDocument.CLASSIFY.getName();
        }

        @Override
        public String getContextualName(GroupClassifyObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(GroupClassifyObservationContext context) {
            var values =
                    KeyValues.of(
                            HighCardinalityKeyNames.INSIGHT_TYPE.withValue(context.insightTypeName),
                            HighCardinalityKeyNames.ITEM_COUNT.withValue(
                                    String.valueOf(context.itemCount)));
            if (context.groupCount != null) {
                values =
                        values.and(
                                HighCardinalityKeyNames.GROUP_COUNT.withValue(
                                        String.valueOf(context.groupCount)));
            }
            return values;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof GroupClassifyObservationContext;
        }
    }
}
