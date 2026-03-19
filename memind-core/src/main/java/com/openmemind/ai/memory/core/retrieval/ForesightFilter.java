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
package com.openmemind.ai.memory.core.retrieval;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import java.time.LocalDate;

/**
 * Foresight Expiration Filter Tool
 *
 */
public final class ForesightFilter {

    private ForesightFilter() {}

    /**
     * Determine if the item is not expired (non-FORESIGHT type always returns true)
     *
     * <p>FORESIGHT type determines validity through metadata.validUntil (ISO date), returns false if expired.
     */
    public static boolean isNotExpired(MemoryItem item) {
        if (item.type() != MemoryItemType.FORESIGHT) {
            return true;
        }
        if (item.metadata() == null) {
            return true;
        }
        Object validUntil = item.metadata().get("validUntil");
        if (validUntil instanceof String dateStr) {
            try {
                return !LocalDate.parse(dateStr).isBefore(LocalDate.now());
            } catch (Exception e) {
                return true;
            }
        }
        return true;
    }
}
