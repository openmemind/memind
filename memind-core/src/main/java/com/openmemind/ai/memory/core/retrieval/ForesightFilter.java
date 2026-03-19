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
