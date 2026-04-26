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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasClass;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityAliasMetadataMergerTest {

    @Test
    void aliasMetadataMergerShouldProduceDeterministicBoundedMetadata() {
        var merged =
                EntityAliasMetadataMerger.merge(
                        Map.of(
                                "topAliases",
                                List.of("OpenAI"),
                                "aliasEvidenceCount",
                                2,
                                "aliasClasses",
                                List.of("explicit_parenthetical"),
                                "firstAliasSeenAt",
                                "2026-04-16T00:00:00Z",
                                "lastAliasSeenAt",
                                "2026-04-17T00:00:00Z"),
                        List.of(
                                alias("开放人工智能", EntityAliasClass.EXPLICIT_PARENTHETICAL),
                                alias("Open AI", EntityAliasClass.SPACING)),
                        Instant.parse("2026-04-18T00:00:00Z"));

        assertThat(merged)
                .containsEntry("firstAliasSeenAt", "2026-04-16T00:00:00Z")
                .containsEntry("lastAliasSeenAt", "2026-04-18T00:00:00Z");
        assertThat(((Number) merged.get("aliasEvidenceCount")).intValue()).isEqualTo(4);
        assertThat((List<String>) merged.get("topAliases"))
                .containsExactly("OpenAI", "Open AI", "开放人工智能");
        assertThat((List<String>) merged.get("aliasClasses"))
                .containsExactly("explicit_parenthetical", "spacing");
    }

    private static EntityAliasObservation alias(String aliasSurface, EntityAliasClass aliasClass) {
        return new EntityAliasObservation(aliasSurface, aliasClass, "entity_inline", 0.93f);
    }
}
