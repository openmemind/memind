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
package com.openmemind.ai.memory.plugin.jdbc.mysql;

import com.openmemind.ai.memory.plugin.jdbc.internal.graph.AbstractJdbcGraphOperations;
import com.openmemind.ai.memory.plugin.jdbc.internal.graph.JdbcGraphDialect;
import javax.sql.DataSource;

public final class MysqlGraphOperations extends AbstractJdbcGraphOperations {

    public MysqlGraphOperations(DataSource dataSource) {
        this(dataSource, true);
    }

    public MysqlGraphOperations(DataSource dataSource, boolean createIfNotExist) {
        super(dataSource, JdbcGraphDialect.MYSQL, createIfNotExist);
    }
}
