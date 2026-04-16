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
package com.openmemind.ai.memory.core.extraction.item.support;

import com.openmemind.ai.memory.core.data.MemoryItem;
import java.util.Map;

/**
 * Resolves the canonical text used for item vectorization and semantic graph linking.
 */
public final class ItemEmbeddingTextResolver {

    private ItemEmbeddingTextResolver() {}

    public static String resolve(ExtractedMemoryEntry entry) {
        return resolve(
                entry != null ? entry.metadata() : null, entry != null ? entry.content() : null);
    }

    public static String resolve(MemoryItem item) {
        return resolve(item != null ? item.metadata() : null, item != null ? item.content() : null);
    }

    private static String resolve(Map<String, Object> metadata, String fallbackContent) {
        if (metadata != null) {
            Object whenToUse = metadata.get("whenToUse");
            if (whenToUse instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return fallbackContent != null ? fallbackContent : "";
    }
}
