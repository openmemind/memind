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
package com.openmemind.ai.memory.core.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContentCapabilityTest {

    @Test
    void shouldOnlyExposeExplicitGovernanceConstructor() {
        assertThat(publicConstructorSignatures())
                .containsExactly(
                        List.of(
                                String.class,
                                String.class,
                                String.class,
                                String.class,
                                Set.class,
                                Set.class,
                                int.class));
    }

    private List<List<Class<?>>> publicConstructorSignatures() {
        return Arrays.stream(ContentCapability.class.getConstructors())
                .map(Constructor::getParameterTypes)
                .map(Arrays::asList)
                .toList();
    }
}
