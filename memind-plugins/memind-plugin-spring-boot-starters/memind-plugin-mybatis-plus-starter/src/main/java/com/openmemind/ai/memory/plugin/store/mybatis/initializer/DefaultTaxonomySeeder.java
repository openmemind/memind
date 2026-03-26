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
package com.openmemind.ai.memory.plugin.store.mybatis.initializer;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.store.MemoryStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

/**
 * Seed default memory taxonomy into store at application startup.
 *
 * <p>Run immediately after schema DDL initialization so default taxonomy is available before
 * user-defined startup runners interact with the extraction pipeline.
 */
public class DefaultTaxonomySeeder implements ApplicationRunner, Ordered {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    private final MemoryStore memoryStore;

    public DefaultTaxonomySeeder(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        memoryStore.insightOperations().upsertInsightTypes(DefaultInsightTypes.all());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
