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
package com.openmemind.ai.memory.plugin.store.mybatis.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InstantTypeHandlerTest {

    private final InstantTypeHandler handler = new InstantTypeHandler();

    @Test
    void readsLegacyEpochMillisValues() throws SQLException {
        ResultSet resultSet =
                (ResultSet)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {ResultSet.class},
                                (proxy, method, args) -> {
                                    if ("getString".equals(method.getName())) {
                                        return "1774200772782";
                                    }
                                    if ("wasNull".equals(method.getName())) {
                                        return false;
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });

        assertThat(handler.getNullableResult(resultSet, "created_at"))
                .isEqualTo(Instant.ofEpochMilli(1774200772782L));
    }
}
