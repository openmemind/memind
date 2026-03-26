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

import com.baomidou.mybatisplus.extension.ddl.IDdl;
import java.util.List;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.springframework.core.Ordered;

public class MemoryStoreDdl implements IDdl, Ordered {

    private final DataSource dataSource;
    private final DatabaseDialectDetector databaseDialectDetector;

    public MemoryStoreDdl(DataSource dataSource, DatabaseDialectDetector databaseDialectDetector) {
        this.dataSource = dataSource;
        this.databaseDialectDetector = databaseDialectDetector;
    }

    @Override
    public List<String> getSqlFiles() {
        return databaseDialectDetector.detect(dataSource).scriptPaths();
    }

    @Override
    public void runScript(Consumer<DataSource> consumer) {
        consumer.accept(dataSource);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
