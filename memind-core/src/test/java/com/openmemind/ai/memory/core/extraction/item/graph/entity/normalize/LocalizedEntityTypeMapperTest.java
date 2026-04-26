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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import org.junit.jupiter.api.Test;

class LocalizedEntityTypeMapperTest {

    @Test
    void localizedTypeMapperRecognizesEnglishAndChineseLabels() {
        var mapper = new LocalizedEntityTypeMapper();

        assertThat(mapper.map("organization")).isEqualTo(GraphEntityType.ORGANIZATION);
        assertThat(mapper.map("人物")).isEqualTo(GraphEntityType.PERSON);
        assertThat(mapper.map("公司")).isEqualTo(GraphEntityType.ORGANIZATION);
        assertThat(mapper.map("地点")).isEqualTo(GraphEntityType.PLACE);
    }

    @Test
    void localizedTypeMapperFallsBackToOtherForUnknownLabels() {
        var mapper = new LocalizedEntityTypeMapper();

        assertThat(mapper.map("未分类标签")).isEqualTo(GraphEntityType.OTHER);
        assertThat(mapper.map(null)).isEqualTo(GraphEntityType.OTHER);
    }
}
