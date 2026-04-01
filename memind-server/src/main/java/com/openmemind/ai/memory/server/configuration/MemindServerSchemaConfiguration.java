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
package com.openmemind.ai.memory.server.configuration;

import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialectDetector;
import com.openmemind.ai.memory.server.store.schema.MemindServerDdl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "memind.store.init-schema",
        havingValue = "true",
        matchIfMissing = true)
public class MemindServerSchemaConfiguration {

    private static final String SQLITE_PREFIX = "jdbc:sqlite:";
    private static final String FILE_PREFIX = "file:";
    private static final String MEMORY_PATH = ":memory:";
    private static final String FILE_MEMORY_PATH = "file::memory:";
    private static final String MEMORY_MODE = "mode=memory";

    @Bean
    static BeanFactoryPostProcessor sqliteDataSourceDirectoryInitializer() {
        return beanFactory -> {
            Environment environment = beanFactory.getBean(Environment.class);
            Path parentDirectory =
                    resolveSqliteParentDirectory(environment.getProperty("spring.datasource.url"));
            if (parentDirectory == null) {
                return;
            }
            try {
                Files.createDirectories(parentDirectory);
            } catch (IOException exception) {
                throw new BeanInitializationException(
                        "Failed to create SQLite parent directory: " + parentDirectory, exception);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    MemindServerDdl memindServerDdl(
            DataSource dataSource, DatabaseDialectDetector databaseDialectDetector) {
        return new MemindServerDdl(dataSource, databaseDialectDetector);
    }

    private static Path resolveSqliteParentDirectory(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }

        String normalizedUrl = jdbcUrl.toLowerCase(Locale.ROOT);
        if (!normalizedUrl.startsWith(SQLITE_PREFIX)) {
            return null;
        }

        String rawLocation = jdbcUrl.substring(SQLITE_PREFIX.length());
        if (rawLocation.isBlank()) {
            return null;
        }

        String normalizedLocation = rawLocation.toLowerCase(Locale.ROOT);
        if (MEMORY_PATH.equals(normalizedLocation) || FILE_MEMORY_PATH.equals(normalizedLocation)) {
            return null;
        }

        int queryIndex = rawLocation.indexOf('?');
        String query = queryIndex >= 0 ? rawLocation.substring(queryIndex + 1) : "";
        if (query.toLowerCase(Locale.ROOT).contains(MEMORY_MODE)) {
            return null;
        }

        String location = queryIndex >= 0 ? rawLocation.substring(0, queryIndex) : rawLocation;
        if (location.toLowerCase(Locale.ROOT).startsWith(FILE_PREFIX)) {
            location = location.substring(FILE_PREFIX.length());
        }
        if (location.isBlank()) {
            return null;
        }

        return Path.of(location).toAbsolutePath().normalize().getParent();
    }
}
