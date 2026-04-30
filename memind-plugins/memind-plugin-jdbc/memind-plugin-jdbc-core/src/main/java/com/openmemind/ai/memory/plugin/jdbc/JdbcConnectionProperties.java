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
package com.openmemind.ai.memory.plugin.jdbc;

import java.util.Map;

public record JdbcConnectionProperties(
        String jdbcUrl,
        String driverClassName,
        String username,
        String password,
        Integer maximumPoolSize,
        Integer minimumIdle,
        String connectionTestQuery,
        String poolName,
        Map<String, String> dataSourceProperties) {

    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";

    public JdbcConnectionProperties {
        dataSourceProperties =
                dataSourceProperties == null ? Map.of() : Map.copyOf(dataSourceProperties);
    }

    public static JdbcConnectionProperties sqlite(String jdbcUrl) {
        return new JdbcConnectionProperties(
                jdbcUrl, SQLITE_DRIVER, null, null, 1, 1, "SELECT 1", null, Map.of());
    }

    public static JdbcConnectionProperties mysql(String jdbcUrl, String username, String password) {
        return new JdbcConnectionProperties(
                jdbcUrl, MYSQL_DRIVER, username, password, 10, 2, null, null, Map.of());
    }

    public static JdbcConnectionProperties postgresql(
            String jdbcUrl, String username, String password) {
        return new JdbcConnectionProperties(
                jdbcUrl, POSTGRESQL_DRIVER, username, password, 10, 2, null, null, Map.of());
    }

    public JdbcConnectionProperties withJdbcUrl(String jdbcUrl) {
        return new JdbcConnectionProperties(
                jdbcUrl,
                driverClassName,
                username,
                password,
                maximumPoolSize,
                minimumIdle,
                connectionTestQuery,
                poolName,
                dataSourceProperties);
    }

    public JdbcConnectionProperties withDriverClassName(String driverClassName) {
        return new JdbcConnectionProperties(
                jdbcUrl,
                driverClassName,
                username,
                password,
                maximumPoolSize,
                minimumIdle,
                connectionTestQuery,
                poolName,
                dataSourceProperties);
    }

    public JdbcConnectionProperties withPoolSize(int maximumPoolSize, int minimumIdle) {
        return new JdbcConnectionProperties(
                jdbcUrl,
                driverClassName,
                username,
                password,
                maximumPoolSize,
                minimumIdle,
                connectionTestQuery,
                poolName,
                dataSourceProperties);
    }
}
