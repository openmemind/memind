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

import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.autoconfigure.DdlAutoConfiguration;
import com.baomidou.mybatisplus.extension.ddl.DdlScriptErrorHandler.ThrowsErrorHandler;
import com.baomidou.mybatisplus.extension.ddl.IDdl;
import com.openmemind.ai.memory.plugin.store.mybatis.MemoryMybatisPlusAutoConfiguration;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@AutoConfiguration(
        before = DdlAutoConfiguration.class,
        after = {DataSourceAutoConfiguration.class, MemoryMybatisPlusAutoConfiguration.class})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(
        name = "memind.store.init-schema",
        havingValue = "true",
        matchIfMissing = true)
public class MemorySchemaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DatabaseDialectDetector databaseDialectDetector() {
        return new DatabaseDialectDetector();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryStoreDdl memoryStoreDdl(
            DataSource dataSource, DatabaseDialectDetector databaseDialectDetector) {
        return new MemoryStoreDdl(dataSource, databaseDialectDetector);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnMissingBean(DdlApplicationRunner.class)
    public DdlApplicationRunner ddlApplicationRunner(List<IDdl> ddlList) {
        DdlApplicationRunner runner = new DdlApplicationRunner(ddlList);
        runner.setThrowException(true);
        runner.setDdlScriptErrorHandler(ThrowsErrorHandler.INSTANCE);
        return runner;
    }
}
