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
package com.openmemind.ai.memory.core.extraction.insight.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class PointIdGeneratorTest {

    @Test
    void generateReturnsUniquePrefixedIds() {
        var ids = new HashSet<String>();
        for (int i = 0; i < 256; i++) {
            ids.add(PointIdGenerator.generate());
        }
        assertThat(ids).hasSize(256);
        assertThat(ids).allMatch(id -> id.startsWith("pt_"));
    }
}
