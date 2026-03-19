package com.openmemind.ai.memory.plugin.store.mybatis.mysql;

import com.baomidou.mybatisplus.extension.ddl.IDdl;
import java.util.List;
import java.util.function.Consumer;
import javax.sql.DataSource;

/**
 * MyBatis-Plus DDL runner — executes the MySQL schema migration file.
 *
 */
public class MemoryDdl implements IDdl {

    private final DataSource dataSource;

    public MemoryDdl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<String> getSqlFiles() {
        return List.of("db/migration/V1__init_memory_store.sql");
    }

    @Override
    public void runScript(Consumer<DataSource> consumer) {
        consumer.accept(dataSource);
    }
}
