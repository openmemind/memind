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
package com.openmemind.ai.memory.core.retrieval.thread;

import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;

/**
 * Deterministic direct-hit seed selection for thread assist.
 */
final class ThreadAssistSeedResolver {

    private static final int MAX_SEED_ITEMS = 3;

    List<ScoredResult> seeds(List<ScoredResult> directWindow) {
        if (directWindow == null || directWindow.isEmpty()) {
            return List.of();
        }
        return directWindow.stream()
                .filter(ThreadAssistSeedResolver::isItemResult)
                .limit(MAX_SEED_ITEMS)
                .toList();
    }

    static List<ScoredResult> directItems(List<ScoredResult> directWindow) {
        if (directWindow == null || directWindow.isEmpty()) {
            return List.of();
        }
        return directWindow.stream().filter(ThreadAssistSeedResolver::isItemResult).toList();
    }

    static Long parseItemIdOrNull(ScoredResult result) {
        if (!isItemResult(result)) {
            return null;
        }
        try {
            return Long.parseLong(result.sourceId());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isItemResult(ScoredResult result) {
        return result != null
                && result.sourceType() == ScoredResult.SourceType.ITEM
                && result.sourceId() != null
                && !result.sourceId().isBlank();
    }
}
