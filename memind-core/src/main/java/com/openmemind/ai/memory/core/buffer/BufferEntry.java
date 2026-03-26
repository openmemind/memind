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
package com.openmemind.ai.memory.core.buffer;

/**
 * Insight buffer entry
 *
 * @param itemId    item business ID
 * @param groupName group name (null when ungrouped)
 * @param built     whether it has been built as Insight
 */
public record BufferEntry(long itemId, String groupName, boolean built) {

    /** Create an ungrouped, unbuilt entry */
    public static BufferEntry ungrouped(long itemId) {
        return new BufferEntry(itemId, null, false);
    }

    /** Whether it is ungrouped */
    public boolean isUngrouped() {
        return groupName == null;
    }
}
