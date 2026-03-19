package com.openmemind.ai.memory.plugin.store.mybatis.initializer;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.store.MemoryStore;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;

/**
 * Seed default memory taxonomy into store at application startup.
 *
 * <p>Use {@link ApplicationStartedEvent} (triggered before CommandLineRunner) to ensure
 * InsightType is written to the database before the extraction pipeline runs, avoiding race conditions in the CommandLineRunner scenario.
 */
public class DefaultTaxonomySeeder implements ApplicationListener<ApplicationStartedEvent> {

    private final MemoryStore memoryStore;

    public DefaultTaxonomySeeder(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        MemoryId systemMemoryId = DefaultMemoryId.of("system", "system");

        DefaultInsightTypes.all()
                .forEach(insightType -> memoryStore.saveInsightType(systemMemoryId, insightType));
    }
}
