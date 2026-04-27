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

import java.time.Instant;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.ArgumentFactory;

final class JdbiTemporalSupport {

    private JdbiTemporalSupport() {}

    static void register(Jdbi jdbi) {
        jdbi.registerColumnMapper(
                Instant.class,
                (resultSet, columnNumber, context) -> {
                    String value = resultSet.getString(columnNumber);
                    return value == null ? null : parseInstant(value);
                });
        ArgumentFactory instantArgumentFactory =
                (type, value, config) -> {
                    if (value instanceof Instant instant) {
                        return Optional.of(
                                (position, statement, context) ->
                                        statement.setString(position, instant.toString()));
                    }
                    return Optional.empty();
                };
        jdbi.registerArgument(instantArgumentFactory);
    }

    private static Instant parseInstant(String value) {
        if (value.chars().allMatch(Character::isDigit)) {
            return Instant.ofEpochMilli(Long.parseLong(value));
        }
        return Instant.parse(value);
    }
}
