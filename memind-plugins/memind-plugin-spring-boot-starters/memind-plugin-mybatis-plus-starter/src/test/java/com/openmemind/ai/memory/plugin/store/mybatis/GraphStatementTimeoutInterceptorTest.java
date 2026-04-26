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
package com.openmemind.ai.memory.plugin.store.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.store.graph.GraphQueryBudgetContext;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Graph statement timeout interceptor")
class GraphStatementTimeoutInterceptorTest {

    @Test
    @DisplayName("graph statement timeout interceptor applies current graph query budget")
    void graphStatementTimeoutInterceptorAppliesCurrentBudget() throws Throwable {
        var interceptor = new GraphStatementTimeoutInterceptor();
        var timeoutSeconds = new AtomicInteger();
        var statement = trackingStatement(timeoutSeconds);
        var invocation = statementPrepareInvocation(statement);

        try (var ignored = GraphQueryBudgetContext.open(Duration.ofMillis(250))) {
            interceptor.intercept(invocation);
        }

        assertThat(timeoutSeconds.get()).isEqualTo(1);
    }

    private static Invocation statementPrepareInvocation(Statement statement)
            throws NoSuchMethodException {
        StatementHandler handler =
                (StatementHandler)
                        Proxy.newProxyInstance(
                                StatementHandler.class.getClassLoader(),
                                new Class<?>[] {StatementHandler.class},
                                (proxy, method, args) -> {
                                    if ("prepare".equals(method.getName())) {
                                        return statement;
                                    }
                                    return defaultValue(method.getReturnType());
                                });
        Method prepareMethod =
                StatementHandler.class.getMethod("prepare", Connection.class, Integer.class);
        return new Invocation(handler, prepareMethod, new Object[] {null, null});
    }

    private static Statement trackingStatement(AtomicInteger timeoutSeconds) {
        return (Statement)
                Proxy.newProxyInstance(
                        Statement.class.getClassLoader(),
                        new Class<?>[] {Statement.class},
                        (proxy, method, args) -> {
                            if ("setQueryTimeout".equals(method.getName())) {
                                timeoutSeconds.set((Integer) args[0]);
                                return null;
                            }
                            return defaultValue(method.getReturnType());
                        });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
