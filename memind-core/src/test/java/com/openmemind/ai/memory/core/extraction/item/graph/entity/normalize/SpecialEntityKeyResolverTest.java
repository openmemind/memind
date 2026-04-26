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

class SpecialEntityKeyResolverTest {

    @Test
    void specialEntityKeyResolverOnlyMapsReservedAnchorsWhenTypeIsSpecial() {
        var resolver = new SpecialEntityKeyResolver();

        assertThat(resolver.mapReservedKey("用户", GraphEntityType.SPECIAL)).contains("special:user");
        assertThat(resolver.mapReservedKey("assistant", GraphEntityType.SPECIAL))
                .contains("special:assistant");
        assertThat(resolver.mapReservedKey("今天", GraphEntityType.SPECIAL)).isEmpty();
        assertThat(resolver.mapReservedKey("assistant", GraphEntityType.ORGANIZATION)).isEmpty();
    }
}
