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

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;

@DisplayName("Database dialect schema scripts")
class DatabaseDialectScriptResourceTest {

    @Test
    void eachDialectUsesSingleSquashedInitScript() {
        assertThat(DatabaseDialect.MYSQL.scriptPaths())
                .containsExactly("db/migration/mysql/V1__init.sql");
        assertThat(DatabaseDialect.POSTGRESQL.scriptPaths())
                .containsExactly("db/migration/postgresql/V1__init.sql");
        assertThat(DatabaseDialect.SQLITE.scriptPaths())
                .containsExactly("db/migration/sqlite/V1__init.sql");
    }

    @ParameterizedTest(name = "{0} scripts should exist")
    @EnumSource(DatabaseDialect.class)
    void eachDialectScriptExists(DatabaseDialect dialect) {
        assertThat(dialect.scriptPaths())
                .allSatisfy(
                        scriptPath ->
                                assertThat(new ClassPathResource(scriptPath).exists()).isTrue());
    }

    @ParameterizedTest(name = "{0} squashed script should contain required schema")
    @EnumSource(DatabaseDialect.class)
    void eachDialectSquashedScriptContainsRequiredSchema(DatabaseDialect dialect)
            throws Exception {
        ClassPathResource resource = new ClassPathResource(dialect.scriptPaths().getFirst());
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains(
                        "memory_item",
                        "memory_raw_data",
                        "memory_resource",
                        "memory_graph_entity",
                        "memory_graph_entity_alias",
                        "memory_item_graph_batch",
                        "memory_graph_alias_batch_receipt",
                        "memory_thread",
                        "memory_thread_runtime",
                        "memory_thread_enrichment_input",
                        "thread_intake_outbox");
        assertThat(sql.toUpperCase()).contains("IF NOT EXISTS");
    }
}
