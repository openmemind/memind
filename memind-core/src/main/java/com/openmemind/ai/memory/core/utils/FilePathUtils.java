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
package com.openmemind.ai.memory.core.utils;

import com.openmemind.ai.memory.core.data.MemoryId;

/**
 * File path safety utilities
 *
 * @author starboyate
 */
public final class FilePathUtils {

    private FilePathUtils() {}

    /**
     * Convert a string to a safe file path component by replacing
     * characters that are not alphanumeric, hyphen, or underscore.
     *
     * <p>Specifically, ':' is replaced with '_' for memoryId identifiers.
     */
    public static String toSafePath(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be null or blank");
        }
        return input.replaceAll("[^a-zA-Z0-9\\-_.]", "_");
    }

    /**
     * Convert a MemoryId to a safe file path component.
     */
    public static String toSafePath(MemoryId memoryId) {
        return toSafePath(memoryId.toIdentifier());
    }
}
