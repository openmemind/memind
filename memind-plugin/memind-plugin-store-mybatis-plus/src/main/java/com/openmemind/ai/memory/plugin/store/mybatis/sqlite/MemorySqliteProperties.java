package com.openmemind.ai.memory.plugin.store.mybatis.sqlite;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SQLite storage configuration properties.
 *
 * <pre>
 * memind:
 *   store:
 *     sqlite:
 *       path: ./memind.db   # File path; fill :memory: for in-memory mode (data loss on restart)
 * </pre>
 *
 */
@ConfigurationProperties("memind.store.sqlite")
public record MemorySqliteProperties(String path) {

    public MemorySqliteProperties {
        if (path == null || path.isBlank()) {
            path = "./memind.db";
        }
    }
}
