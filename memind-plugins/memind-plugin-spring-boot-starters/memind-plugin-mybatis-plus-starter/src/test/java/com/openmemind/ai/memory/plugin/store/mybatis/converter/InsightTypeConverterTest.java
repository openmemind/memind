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
package com.openmemind.ai.memory.plugin.store.mybatis.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeConfig;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightTypeDO;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InsightTypeConverter")
class InsightTypeConverterTest {

    private static final Instant BASE_TIME = Instant.parse("2026-03-21T00:00:00Z");

    @Test
    @DisplayName("toRecord does not require memoryId")
    void toRecordDoesNotRequireMemoryId() {
        var dataObject = new MemoryInsightTypeDO();
        dataObject.setBizId(7L);
        dataObject.setName("profile");
        dataObject.setDescription("profile description");
        dataObject.setDescriptionVectorId("vec-1");
        dataObject.setTargetTokens(512);
        dataObject.setCategories(List.of("profile"));
        dataObject.setLastUpdatedAt(BASE_TIME);
        dataObject.setCreatedAt(BASE_TIME);
        dataObject.setUpdatedAt(BASE_TIME.plusSeconds(30));
        dataObject.setAnalysisMode(InsightAnalysisMode.ROOT.name());
        dataObject.setScope(MemoryScope.USER.name());

        MemoryInsightType record = InsightTypeConverter.toRecord(dataObject);

        assertThat(record.id()).isEqualTo(7L);
        assertThat(record.name()).isEqualTo("profile");
        assertThat(record.scope()).isEqualTo(MemoryScope.USER);
    }

    @Test
    @DisplayName("toDO preserves prompt scope and mode without memoryId")
    void toDoPreservesPromptScopeAndModeWithoutMemoryId() {
        var record =
                new MemoryInsightType(
                        9L,
                        "profile",
                        "profile description",
                        "vec-9",
                        List.of("profile"),
                        640,
                        BASE_TIME,
                        BASE_TIME,
                        BASE_TIME.plusSeconds(60),
                        InsightAnalysisMode.ROOT,
                        InsightTreeConfig.defaults(),
                        MemoryScope.USER);

        var dataObject = InsightTypeConverter.toDO(record);

        assertThat(dataObject.getBizId()).isEqualTo(9L);
        assertThat(dataObject.getAnalysisMode()).isEqualTo(InsightAnalysisMode.ROOT.name());
        assertThat(dataObject.getScope()).isEqualTo(MemoryScope.USER.name());
    }
}
