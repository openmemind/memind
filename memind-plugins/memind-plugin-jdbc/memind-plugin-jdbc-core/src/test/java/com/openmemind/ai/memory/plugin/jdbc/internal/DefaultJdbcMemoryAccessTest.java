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
package com.openmemind.ai.memory.plugin.jdbc.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DefaultJdbcMemoryAccessTest {

    @Test
    void closeClosesOwnedResourcesInOrder() throws Exception {
        List<String> closed = new ArrayList<>();
        AutoCloseable first = () -> closed.add("first");
        AutoCloseable second = () -> closed.add("second");

        DefaultJdbcMemoryAccess access =
                new DefaultJdbcMemoryAccess(
                        stub(MemoryStore.class),
                        stub(MemoryBuffer.class),
                        stub(MemoryTextSearch.class),
                        stub(BubbleTrackerStore.class),
                        stub(DataSource.class),
                        List.of(first, second));

        access.close();

        assertThat(closed).containsExactly("first", "second");
    }

    @Test
    void closeSuppressesFailuresAfterFirstOwnedResourceFailure() {
        IllegalStateException firstFailure = new IllegalStateException("first");
        IllegalArgumentException secondFailure = new IllegalArgumentException("second");
        AutoCloseable first =
                () -> {
                    throw firstFailure;
                };
        AutoCloseable second =
                () -> {
                    throw secondFailure;
                };
        DefaultJdbcMemoryAccess access =
                new DefaultJdbcMemoryAccess(
                        stub(MemoryStore.class),
                        stub(MemoryBuffer.class),
                        stub(MemoryTextSearch.class),
                        stub(BubbleTrackerStore.class),
                        stub(DataSource.class),
                        List.of(first, second));

        assertThatThrownBy(access::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to close JDBC memory access")
                .hasCause(firstFailure)
                .satisfies(
                        throwable ->
                                assertThat(throwable.getSuppressed())
                                        .containsExactly(secondFailure));
    }

    private static <T> T stub(Class<T> type) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            Class<?> returnType = method.getReturnType();
                            if (returnType == boolean.class) {
                                return false;
                            }
                            if (returnType.isPrimitive()) {
                                return 0;
                            }
                            return null;
                        }));
    }
}
