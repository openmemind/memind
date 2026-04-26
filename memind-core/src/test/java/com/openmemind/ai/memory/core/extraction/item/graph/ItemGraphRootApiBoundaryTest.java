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
package com.openmemind.ai.memory.core.extraction.item.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ItemGraphRootApiBoundaryTest {

    private static final String ROOT_PACKAGE =
            "com.openmemind.ai.memory.core.extraction.item.graph";

    @Test
    void rootPackageShouldExposeStableVocabularyAndHideInternalImplementations() throws Exception {
        List<String> stableTypes =
                List.of(
                        ROOT_PACKAGE + ".ItemGraphMaterializer",
                        ROOT_PACKAGE + ".NoOpItemGraphMaterializer",
                        ROOT_PACKAGE + ".ItemGraphMaterializationResult",
                        ROOT_PACKAGE + ".EntityResolutionMode",
                        ROOT_PACKAGE + ".AliasEvidenceMode",
                        ROOT_PACKAGE + ".CrossScriptMergePolicy",
                        ROOT_PACKAGE + ".UserAliasDictionary",
                        ROOT_PACKAGE + ".EntityAliasClass",
                        ROOT_PACKAGE + ".EntityAliasObservation");

        for (String typeName : stableTypes) {
            assertThat(Class.forName(typeName).getPackageName()).isEqualTo(ROOT_PACKAGE);
        }

        assertThatThrownBy(() -> Class.forName(ROOT_PACKAGE + ".DefaultItemGraphMaterializer"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(ROOT_PACKAGE + ".GraphHintNormalizer"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
