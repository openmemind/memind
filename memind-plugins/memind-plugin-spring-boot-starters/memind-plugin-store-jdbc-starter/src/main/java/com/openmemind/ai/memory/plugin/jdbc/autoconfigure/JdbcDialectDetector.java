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
package com.openmemind.ai.memory.plugin.jdbc.autoconfigure;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;

class JdbcDialectDetector {

    JdbcDialect detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = normalize(connection.getMetaData().getDatabaseProductName());
            String jdbcUrl = normalize(connection.getMetaData().getURL());

            if (productName.contains("sqlite") || jdbcUrl.contains(":sqlite:")) {
                return JdbcDialect.SQLITE;
            }
            if (productName.contains("mysql") || jdbcUrl.contains(":mysql:")) {
                return JdbcDialect.MYSQL;
            }
            if (productName.contains("postgresql") || jdbcUrl.contains(":postgresql:")) {
                return JdbcDialect.POSTGRESQL;
            }

            throw new IllegalStateException(
                    "memind-provider-store-jdbc-starter does not support DataSource product '"
                            + connection.getMetaData().getDatabaseProductName()
                            + "' with URL '"
                            + connection.getMetaData().getURL()
                            + "'");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to inspect DataSource metadata", e);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
