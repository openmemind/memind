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

import com.openmemind.ai.memory.core.store.graph.GraphQueryBudgetContext;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;

@Intercepts(
        @Signature(
                type = StatementHandler.class,
                method = "prepare",
                args = {java.sql.Connection.class, Integer.class}))
public final class GraphStatementTimeoutInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        var timeout =
                GraphQueryBudgetContext.currentTimeout()
                        .map(Duration::toMillis)
                        .filter(timeoutMs -> timeoutMs > 0);
        if (timeout.isEmpty()) {
            return invocation.proceed();
        }

        Object result = invocation.proceed();
        var statement = (Statement) result;
        timeout.map(timeoutMs -> (int) Math.max(1L, (timeoutMs + 999L) / 1000L))
                .ifPresent(
                        seconds -> {
                            try {
                                statement.setQueryTimeout(seconds);
                            } catch (SQLException ignored) {
                                // Graph reads stay best-effort; caller-level timeout remains
                                // authoritative.
                            }
                        });
        return result;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {}
}
