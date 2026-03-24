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
package com.openmemind.ai.memory.plugin.store.mybatis.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;

@DisplayName("Database dialect schema scripts")
class DatabaseDialectScriptResourceTest {

    @ParameterizedTest(name = "{0} scripts should exist")
    @EnumSource(DatabaseDialect.class)
    void eachDialectScriptExists(DatabaseDialect dialect) {
        assertThat(dialect.scriptPaths())
                .allSatisfy(
                        scriptPath ->
                                assertThat(new ClassPathResource(scriptPath).exists()).isTrue());
    }
}
