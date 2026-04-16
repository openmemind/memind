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
package com.openmemind.ai.memory.core.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.utils.JsonUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InsightPointTest {

    @Test
    void deserializeLegacyConfidenceIsIgnored() throws Exception {
        var mapper = JsonUtils.newMapper();

        var point =
                mapper.readValue(
                        """
                        {
                          "pointId": "pt_leaf_1",
                          "type": "SUMMARY",
                          "content": "User prefers async communication",
                          "confidence": 0.85,
                          "sourceItemIds": ["10", "11"]
                        }
                        """,
                        InsightPoint.class);

        assertThat(point.pointId()).isEqualTo("pt_leaf_1");
        assertThat(point.sourceItemIds()).containsExactly("10", "11");
        assertThat(point.sourcePointRefs()).isEmpty();
    }

    @Test
    void serializeBranchPointContainsSourcePointRefs() throws Exception {
        var mapper = JsonUtils.newMapper();
        var point =
                new InsightPoint(
                        "pt_branch_1",
                        InsightPoint.PointType.SUMMARY,
                        "User consistently optimizes for async-first work.",
                        List.of(),
                        List.of(
                                new InsightPointRef(101L, "pt_leaf_remote"),
                                new InsightPointRef(102L, "pt_leaf_deep_work")),
                        Map.of("dimension", "work_style"));

        String json = mapper.writeValueAsString(point);

        assertThat(json).contains("sourcePointRefs");
        assertThat(json).doesNotContain("confidence");
    }

    @Test
    void withPointIdCopiesRecordWithoutChangingEvidenceFields() {
        var original =
                new InsightPoint(
                        null,
                        InsightPoint.PointType.REASONING,
                        "The user values uninterrupted work blocks",
                        List.of("22", "24"),
                        List.of(new InsightPointRef(101L, "pt_leaf_focus")),
                        Map.of("dimension", "focus"));

        var updated = original.withPointId("pt_456");

        assertThat(updated.pointId()).isEqualTo("pt_456");
        assertThat(updated.type()).isEqualTo(original.type());
        assertThat(updated.content()).isEqualTo(original.content());
        assertThat(updated.sourceItemIds()).containsExactly("22", "24");
        assertThat(updated.sourcePointRefs())
                .containsExactly(new InsightPointRef(101L, "pt_leaf_focus"));
        assertThat(updated.metadata()).containsEntry("dimension", "focus");
    }
}
