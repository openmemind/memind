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
    void deserializeLegacyJsonWithoutPointIdKeepsDataCompatible() throws Exception {
        var mapper = JsonUtils.newMapper();

        var point =
                mapper.readValue(
                        """
                        {
                          "type": "SUMMARY",
                          "content": "User prefers async communication",
                          "confidence": 0.9,
                          "sourceItemIds": ["10", "11"]
                        }
                        """,
                        InsightPoint.class);

        assertThat(point.pointId()).isNull();
        assertThat(point.content()).isEqualTo("User prefers async communication");
        assertThat(point.sourceItemIds()).containsExactly("10", "11");
    }

    @Test
    void withPointIdCopiesRecordWithoutChangingOtherFields() {
        var original =
                new InsightPoint(
                        InsightPoint.PointType.SUMMARY,
                        "User prefers async communication",
                        0.9f,
                        List.of("10", "11"));

        var updated = original.withPointId("pt_123");

        assertThat(updated.pointId()).isEqualTo("pt_123");
        assertThat(updated.type()).isEqualTo(original.type());
        assertThat(updated.content()).isEqualTo(original.content());
        assertThat(updated.sourceItemIds()).containsExactly("10", "11");
    }

    @Test
    void canonicalConstructorAcceptsExplicitPointIdAndMetadata() {
        var point =
                new InsightPoint(
                        "pt_456",
                        InsightPoint.PointType.REASONING,
                        "The user values uninterrupted work blocks",
                        0.75f,
                        List.of("22", "24"),
                        Map.of("dimension", "focus"));

        assertThat(point.pointId()).isEqualTo("pt_456");
        assertThat(point.metadata()).containsEntry("dimension", "focus");
    }
}
