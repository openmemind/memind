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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;

public class DatabaseDialectDetector {

    public DatabaseDialect detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            return detect(metadata.getDatabaseProductName(), metadata.getURL());
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to inspect datasource metadata", ex);
        }
    }

    DatabaseDialect detect(String productName, String url) {
        String normalizedProduct = normalize(productName);
        String normalizedUrl = normalize(url);

        if (normalizedProduct.contains("sqlite") || normalizedUrl.contains(":sqlite:")) {
            return DatabaseDialect.SQLITE;
        }
        if (normalizedProduct.contains("mysql") || normalizedUrl.contains(":mysql:")) {
            return DatabaseDialect.MYSQL;
        }
        if (normalizedProduct.contains("postgresql") || normalizedUrl.contains(":postgresql:")) {
            return DatabaseDialect.POSTGRESQL;
        }

        throw new IllegalStateException(
                "Unsupported database dialect. productName=" + productName + ", url=" + url);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
