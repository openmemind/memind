package com.openmemind.ai.memory.plugin.store.mybatis.initializer;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memind.store")
public record MemoryStoreProperties(boolean initSchema) {

    public MemoryStoreProperties {}

    public MemoryStoreProperties() {
        this(true);
    }
}
