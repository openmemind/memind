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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HikariDataSourceFactoryTest {

    @Test
    void createAppliesConnectionAndPoolProperties() {
        JdbcConnectionProperties properties =
                new JdbcConnectionProperties(
                        "jdbc:sqlite::memory:",
                        "org.sqlite.JDBC",
                        "user",
                        "secret",
                        3,
                        1,
                        "SELECT 1",
                        "memind-test",
                        Map.of("cachePrepStmts", "true"));

        try (HikariDataSource dataSource = HikariDataSourceFactory.create(properties)) {
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:sqlite::memory:");
            assertThat(dataSource.getDriverClassName()).isEqualTo("org.sqlite.JDBC");
            assertThat(dataSource.getUsername()).isEqualTo("user");
            assertThat(dataSource.getPassword()).isEqualTo("secret");
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(3);
            assertThat(dataSource.getMinimumIdle()).isEqualTo(1);
            assertThat(dataSource.getConnectionTestQuery()).isEqualTo("SELECT 1");
            assertThat(dataSource.getPoolName()).isEqualTo("memind-test");
            assertThat(dataSource.getDataSourceProperties())
                    .containsEntry("cachePrepStmts", "true");
        }
    }

    @Test
    void createDefaultsToServerPoolSizeWhenPoolSettingsAreOmitted() {
        JdbcConnectionProperties properties =
                new JdbcConnectionProperties(
                        "jdbc:sqlite::memory:",
                        "org.sqlite.JDBC",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of());

        try (HikariDataSource dataSource = HikariDataSourceFactory.create(properties)) {
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
            assertThat(dataSource.getMinimumIdle()).isEqualTo(2);
        }
    }

    @Test
    void sqliteDefaultsCanBeRequestedOnConnectionProperties() {
        JdbcConnectionProperties properties =
                JdbcConnectionProperties.sqlite("jdbc:sqlite::memory:");

        try (HikariDataSource dataSource = HikariDataSourceFactory.create(properties)) {
            assertThat(dataSource.getDriverClassName()).isEqualTo("org.sqlite.JDBC");
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(1);
            assertThat(dataSource.getMinimumIdle()).isEqualTo(1);
        }
    }

    @Test
    void mysqlAndPostgresqlDefaultsCanBeRequestedOnConnectionProperties() {
        JdbcConnectionProperties mysql =
                JdbcConnectionProperties.mysql(
                        "jdbc:mysql://localhost:3306/memind", "root", "secret");
        JdbcConnectionProperties postgresql =
                JdbcConnectionProperties.postgresql(
                        "jdbc:postgresql://localhost:5432/memind", "postgres", "secret");

        assertThat(mysql.driverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(mysql.maximumPoolSize()).isEqualTo(10);
        assertThat(mysql.minimumIdle()).isEqualTo(2);
        assertThat(postgresql.driverClassName()).isEqualTo("org.postgresql.Driver");
        assertThat(postgresql.maximumPoolSize()).isEqualTo(10);
        assertThat(postgresql.minimumIdle()).isEqualTo(2);
    }

    @Test
    void createRejectsBlankUrlOrDriverClassBeforeCreatingPool() {
        JdbcConnectionProperties blankUrl =
                new JdbcConnectionProperties(
                        " ", "org.sqlite.JDBC", null, null, null, null, null, null, Map.of());
        JdbcConnectionProperties blankDriver =
                new JdbcConnectionProperties(
                        "jdbc:sqlite::memory:", "", null, null, null, null, null, null, Map.of());

        assertThatThrownBy(() -> HikariDataSourceFactory.create(blankUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcUrl must not be blank");
        assertThatThrownBy(() -> HikariDataSourceFactory.create(blankDriver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("driverClassName must not be blank");
    }
}
