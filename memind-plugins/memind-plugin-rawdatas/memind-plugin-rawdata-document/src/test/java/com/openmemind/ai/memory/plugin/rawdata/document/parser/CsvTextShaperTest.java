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
package com.openmemind.ai.memory.plugin.rawdata.document.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvTextShaperTest {

    @Test
    void shapeUsesHeaderNamesWhenPresent() {
        String csv = "name,role,team\nAlice,PM,Core\nBob,Engineer,AI\n";

        assertThat(new CsvTextShaper().shape(csv))
                .isEqualTo(
                        String.join(
                                "\n",
                                "Row 1:",
                                "name: Alice, role: PM, team: Core",
                                "",
                                "Row 2:",
                                "name: Bob, role: Engineer, team: AI"));
    }

    @Test
    void shapeAutoUsesStableSyntheticKeysWhenNoHeaderExists() {
        String csv = "Alice,PM\nBob,Engineer\n";

        assertThat(new CsvTextShaper().shape(csv))
                .isEqualTo(
                        String.join(
                                "\n",
                                "Row 1:",
                                "column1: Alice, column2: PM",
                                "",
                                "Row 2:",
                                "column1: Bob, column2: Engineer"));
    }

    @Test
    void shapeUsesHeaderNamesForLowercaseHeaderAndLowercaseRows() {
        String csv = "name,team\nalice,core\nbob,ai\n";

        assertThat(new CsvTextShaper().shape(csv))
                .isEqualTo(
                        String.join(
                                "\n",
                                "Row 1:",
                                "name: alice, team: core",
                                "",
                                "Row 2:",
                                "name: bob, team: ai"));
    }
}
