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
package com.openmemind.ai.memory.plugin.jdbc.internal.jdbi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

class JdbiFactoryTest {

    @Test
    void createRegistersBasicTemporalSupport() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite::memory:");
        Instant instant = Instant.parse("2026-04-27T07:00:00Z");

        Instant result =
                JdbiFactory.create(dataSource)
                        .withHandle(
                                handle -> {
                                    handle.execute("CREATE TABLE sample(value TEXT)");
                                    handle.createUpdate("INSERT INTO sample(value) VALUES (:value)")
                                            .bind("value", instant)
                                            .execute();
                                    return handle.createQuery("SELECT value FROM sample")
                                            .mapTo(Instant.class)
                                            .one();
                                });

        assertThat(result).isEqualTo(instant);
    }
}
